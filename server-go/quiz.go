package main

import (
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"os"
	"path/filepath"
	"sort"
	"sync"
	"time"
)

// Question is the server-side (full) representation. The Answer field is never
// sent to clients. Content / Options are rich text (Markdown) rendered by the
// Android app.
type Question struct {
	ID      string      `json:"id"`
	Type    string      `json:"type"` // single | multiple | bool
	Content string      `json:"content"`
	Options []string    `json:"options"`
	Answer  interface{} `json:"answer"` // index | []index | bool
	Reward  int         `json:"reward"`
}

// QuestionPublic is what the client receives: no answer, but content/options
// (rich text) and the reward are included.
type QuestionPublic struct {
	ID      string   `json:"id"`
	Type    string   `json:"type"`
	Content string   `json:"content"`
	Options []string `json:"options"`
	Reward  int      `json:"reward"`
}

type claimRecord struct {
	Day    string `json:"day"`
	Amount int    `json:"amount"`
	At     int64  `json:"at"`
}

type answerSubmission struct {
	ID    string      `json:"id"`
	Value interface{} `json:"value"`
}

type submitRequest struct {
	Token   string             `json:"token"`
	Answers []answerSubmission `json:"answers"`
}

// QuizStore owns the question set and the per-user claim ledger, and delegates
// reward issuance to the GPC client.
type QuizStore struct {
	mu            sync.Mutex
	dataDir       string
	quizPath      string
	claimsPath    string
	submittedPath string
	claims        map[string]map[string]claimRecord
	submitted     map[string]bool
	gpc           *gpcClient
	dailyLimit    int
	scope         string
	redirectURI   string
}

func NewQuizStore(dataDir string, gpc *gpcClient, dailyLimit int, scope, redirectURI string) *QuizStore {
	qs := &QuizStore{
		dataDir:       dataDir,
		quizPath:      filepath.Join(dataDir, "quiz.json"),
		claimsPath:    filepath.Join(dataDir, "quiz_claims.json"),
		submittedPath: filepath.Join(dataDir, "quiz_submitted.json"),
		claims:        map[string]map[string]claimRecord{},
		submitted:     map[string]bool{},
		gpc:           gpc,
		dailyLimit:    dailyLimit,
		scope:         scope,
		redirectURI:   redirectURI,
	}
	qs.loadClaims()
	qs.loadSubmitted()
	return qs
}

func (qs *QuizStore) loadClaims() {
	data, err := os.ReadFile(qs.claimsPath)
	if err == nil {
		_ = json.Unmarshal(data, &qs.claims)
	}
	if qs.claims == nil {
		qs.claims = map[string]map[string]claimRecord{}
	}
}

func (qs *QuizStore) saveClaims() {
	if b, err := json.MarshalIndent(qs.claims, "", "  "); err == nil {
		_ = os.WriteFile(qs.claimsPath, b, 0644)
	}
}

// loadSubmitted / saveSubmitted persist the per-user "already submitted the
// whole quiz" flag so a user can only ever submit once, across restarts.
func (qs *QuizStore) loadSubmitted() {
	data, err := os.ReadFile(qs.submittedPath)
	if err == nil {
		_ = json.Unmarshal(data, &qs.submitted)
	}
	if qs.submitted == nil {
		qs.submitted = map[string]bool{}
	}
}

func (qs *QuizStore) saveSubmitted() {
	if b, err := json.MarshalIndent(qs.submitted, "", "  "); err == nil {
		_ = os.WriteFile(qs.submittedPath, b, 0644)
	}
}

func (qs *QuizStore) loadQuestions() ([]Question, error) {
	data, err := os.ReadFile(qs.quizPath)
	if err != nil {
		return nil, err
	}
	var out struct {
		Questions []Question `json:"questions"`
	}
	if err := json.Unmarshal(data, &out); err != nil {
		return nil, err
	}
	return out.Questions, nil
}

func (qs *QuizStore) publicQuestions() ([]QuestionPublic, error) {
	all, err := qs.loadQuestions()
	if err != nil {
		return nil, err
	}
	pub := make([]QuestionPublic, 0, len(all))
	for _, q := range all {
		pub = append(pub, QuestionPublic{
			ID:      q.ID,
			Type:    q.Type,
			Content: q.Content,
			Options: q.Options,
			Reward:  q.Reward,
		})
	}
	return pub, nil
}

func (qs *QuizStore) todayKey() string {
	t := time.Now()
	return fmt.Sprintf("%d-%d-%d", t.Year(), t.Month(), t.Day())
}

func (qs *QuizStore) dailyAwarded(userID string) int {
	k := qs.todayKey()
	sum := 0
	for _, c := range qs.claims[userID] {
		if c.Day == k {
			sum += c.Amount
		}
	}
	return sum
}

// GET /api/quiz/questions — returns the question set without answers.
func (qs *QuizStore) questionsHandler(w http.ResponseWriter, r *http.Request) {
	pub, err := qs.publicQuestions()
	if err != nil {
		sendError(w, http.StatusInternalServerError, "读取题目失败")
		return
	}
	sendJSON(w, http.StatusOK, map[string]interface{}{"success": true, "data": pub})
}

// GET /api/oauth/gpc-config — hands the Android app the OAuth client it needs
// to perform the GPC authorization-code flow.
func (qs *QuizStore) gpcConfigHandler(w http.ResponseWriter, r *http.Request) {
	log.Println("[gpc-config] handler start")
	creds, err := qs.gpc.ensureOAuthClient()
	if err != nil {
		log.Println("[gpc-config] ensureOAuthClient error:", err)
		sendError(w, http.StatusInternalServerError, "获取 GPC OAuth 配置失败: "+err.Error())
		return
	}
	log.Println("[gpc-config] client:", creds.ClientID)
	sendJSON(w, http.StatusOK, map[string]interface{}{
		"clientId":    creds.ClientID,
		"clientSecret": creds.ClientSecret,
		"gpcBaseUrl":  qs.gpc.baseURL(),
		"redirectUri": qs.redirectURI,
		"scope":       qs.scope,
	})
}

// POST /api/quiz/submit — validates answers, mints rewards for correct and
// not-yet-claimed questions, enforces the daily cap, and returns a per-question
// result plus the user's new balance.
func (qs *QuizStore) submitHandler(w http.ResponseWriter, r *http.Request) {
	var req submitRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		sendError(w, http.StatusBadRequest, "请求体解析失败")
		return
	}
	if req.Token == "" {
		sendError(w, http.StatusUnauthorized, "请先授权金猪币账号")
		return
	}
	userID, err := qs.gpc.VerifyToken(req.Token)
	if err != nil {
		sendError(w, http.StatusUnauthorized, "金猪币账号校验失败: "+err.Error())
		return
	}
	questions, err := qs.loadQuestions()
	if err != nil {
		sendError(w, http.StatusInternalServerError, "读取题目失败")
		return
	}
	ansMap := map[string]interface{}{}
	for _, a := range req.Answers {
		ansMap[a.ID] = a.Value
	}

	qs.mu.Lock()
	defer qs.mu.Unlock()

	// One-time guard: the whole quiz may only be submitted once per user.
	if qs.submitted[userID] {
		sendJSON(w, http.StatusOK, map[string]interface{}{
			"success":          false,
			"alreadySubmitted": true,
			"message":          "您已提交过问卷，无法重复提交",
		})
		return
	}

	userClaims := qs.claims[userID]
	if userClaims == nil {
		userClaims = map[string]claimRecord{}
		qs.claims[userID] = userClaims
	}
	dailyUsed := qs.dailyAwarded(userID)
	round := 0

	type qres struct {
		ID             string `json:"id"`
		Correct        bool   `json:"correct"`
		Awarded        int    `json:"awarded"`
		AlreadyClaimed bool   `json:"alreadyClaimed,omitempty"`
		Limited        bool   `json:"limited,omitempty"`
	}
	results := make([]qres, 0, len(questions))

	for _, q := range questions {
		val, ok := ansMap[q.ID]
		if !ok {
			results = append(results, qres{ID: q.ID, Correct: false})
			continue
		}
		if !isCorrect(q, val) {
			results = append(results, qres{ID: q.ID, Correct: false})
			continue
		}
		if _, done := userClaims[q.ID]; done {
			results = append(results, qres{ID: q.ID, Correct: true, AlreadyClaimed: true})
			continue
		}
		if qs.dailyLimit > 0 && dailyUsed+round+q.Reward > qs.dailyLimit {
			results = append(results, qres{ID: q.ID, Correct: true, Limited: true})
			continue
		}
		if err := qs.gpc.MintCoins(userID, q.Reward, "答题奖励 "+q.ID); err != nil {
			results = append(results, qres{ID: q.ID, Correct: true})
			continue
		}
		userClaims[q.ID] = claimRecord{Day: qs.todayKey(), Amount: q.Reward, At: time.Now().UnixMilli()}
		round += q.Reward
		results = append(results, qres{ID: q.ID, Correct: true, Awarded: q.Reward})
	}
	// Mark the whole quiz as submitted by this user so it can never be
	// submitted again, then persist both ledgers.
	qs.submitted[userID] = true
	qs.saveClaims()
	qs.saveSubmitted()

	balance := 0
	if b, err := qs.gpc.GetBalance(req.Token); err == nil {
		balance = b
	}

	sendJSON(w, http.StatusOK, map[string]interface{}{
		"success":      true,
		"results":      results,
		"totalAwarded": round,
		"balance":      balance,
		"dailyUsed":    dailyUsed + round,
		"dailyLimit":   qs.dailyLimit,
	})
}

// GET /api/quiz/status?token=... — reports whether the token's user has
// already submitted the quiz, so clients can disable re-submission (and show
// the correct state) even after a restart.
func (qs *QuizStore) statusHandler(w http.ResponseWriter, r *http.Request) {
	token := r.URL.Query().Get("token")
	if token == "" {
		sendError(w, http.StatusUnauthorized, "请先授权金猪币账号")
		return
	}
	userID, err := qs.gpc.VerifyToken(token)
	if err != nil {
		sendError(w, http.StatusUnauthorized, "金猪币账号校验失败: "+err.Error())
		return
	}
	qs.mu.Lock()
	submitted := qs.submitted[userID]
	qs.mu.Unlock()
	sendJSON(w, http.StatusOK, map[string]interface{}{
		"success":   true,
		"submitted": submitted,
	})
}

// isCorrect compares the user-submitted value with the stored answer, handling
// the three objective question types.
func isCorrect(q Question, value interface{}) bool {
	switch q.Type {
	case "single":
		return toFloat(value) == toFloat(q.Answer)
	case "bool":
		return toBool(value) == toBool(q.Answer)
	case "multiple":
		a := toFloatSlice(value)
		b := toFloatSlice(q.Answer)
		if len(a) != len(b) {
			return false
		}
		sa := append([]float64{}, a...)
		sb := append([]float64{}, b...)
		sort.Float64s(sa)
		sort.Float64s(sb)
		for i := range sa {
			if sa[i] != sb[i] {
				return false
			}
		}
		return true
	}
	return false
}

func toFloat(v interface{}) float64 {
	switch n := v.(type) {
	case float64:
		return n
	case float32:
		return float64(n)
	case int:
		return float64(n)
	case int64:
		return float64(n)
	}
	return -1
}

func toBool(v interface{}) bool {
	b, _ := v.(bool)
	return b
}

func toFloatSlice(v interface{}) []float64 {
	arr, ok := v.([]interface{})
	if !ok {
		return nil
	}
	out := make([]float64, 0, len(arr))
	for _, e := range arr {
		out = append(out, toFloat(e))
	}
	return out
}
