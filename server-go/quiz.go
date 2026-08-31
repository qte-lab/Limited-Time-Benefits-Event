package main

import (
	"encoding/json"
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

// claimRecord is kept purely so a single submission can never mint the same
// question's reward twice (idempotency for a period). There is no daily cap.
type claimRecord struct {
	Amount int   `json:"amount"`
	At     int64 `json:"at"`
}

type answerSubmission struct {
	ID    string      `json:"id"`
	Value interface{} `json:"value"`
}

type submitRequest struct {
	Token   string             `json:"token"`
	Answers []answerSubmission `json:"answers"`
}

// qres is the per-question grading returned to the client.
type qres struct {
	ID             string `json:"id"`
	Correct        bool   `json:"correct"`
	Awarded        int    `json:"awarded"`
	AlreadyClaimed bool   `json:"alreadyClaimed,omitempty"`
}

// SubmissionRecord is the full, server-side record of one user's submission for
// one quiz period. It lets clients render the user's own answers and the
// grading again later, without re-submitting.
type SubmissionRecord struct {
	SubmittedAt  int64             `json:"submittedAt"`
	TotalAwarded int              `json:"totalAwarded"`
	Answers      []answerSubmission `json:"answers"`
	Results      []qres           `json:"results"`
}

// QuizStore owns the question set, the current period, and the per-user
// submission ledger, and delegates reward issuance to the GPC client.
type QuizStore struct {
	mu              sync.Mutex
	dataDir         string
	quizPath        string
	claimsPath      string
	submissionsPath string
	// submissions[userID][period] = record
	submissions map[string]map[string]*SubmissionRecord
	// claims[userID][period + "\x00" + qID] = record (minting idempotency)
	claims      map[string]map[string]claimRecord
	gpc         *gpcClient
	scope       string
	redirectURI string
}

func NewQuizStore(dataDir string, gpc *gpcClient, scope, redirectURI string) *QuizStore {
	qs := &QuizStore{
		dataDir:         dataDir,
		quizPath:        filepath.Join(dataDir, "quiz.json"),
		claimsPath:      filepath.Join(dataDir, "quiz_claims.json"),
		submissionsPath: filepath.Join(dataDir, "quiz_submissions.json"),
		submissions:     map[string]map[string]*SubmissionRecord{},
		claims:          map[string]map[string]claimRecord{},
		gpc:             gpc,
		scope:           scope,
		redirectURI:     redirectURI,
	}
	qs.loadClaims()
	qs.loadSubmissions()
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

// loadSubmissions / saveSubmissions persist the per-user, per-period submission
// records so a user can review their answers and grading across restarts, and
// so the whole quiz can only ever be submitted once per period.
func (qs *QuizStore) loadSubmissions() {
	data, err := os.ReadFile(qs.submissionsPath)
	if err == nil {
		_ = json.Unmarshal(data, &qs.submissions)
	}
	if qs.submissions == nil {
		qs.submissions = map[string]map[string]*SubmissionRecord{}
	}
}

func (qs *QuizStore) saveSubmissions() {
	if b, err := json.MarshalIndent(qs.submissions, "", "  "); err == nil {
		_ = os.WriteFile(qs.submissionsPath, b, 0644)
	}
}

// loadQuiz reads the current question set and its period identifier. The period
// is what makes "分期" (quiz issues) work: deploying a quiz.json with a new
// period lets every user answer again for that new issue.
func (qs *QuizStore) loadQuiz() (string, []Question, error) {
	data, err := os.ReadFile(qs.quizPath)
	if err != nil {
		return "", nil, err
	}
	var out struct {
		Period   string     `json:"period"`
		Questions []Question `json:"questions"`
	}
	if err := json.Unmarshal(data, &out); err != nil {
		return "", nil, err
	}
	return out.Period, out.Questions, nil
}

func (qs *QuizStore) publicQuestions() ([]QuestionPublic, error) {
	_, all, err := qs.loadQuiz()
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

// GET /api/quiz/questions — returns the period and the question set (answers
// stripped server-side).
func (qs *QuizStore) questionsHandler(w http.ResponseWriter, r *http.Request) {
	period, _, err := qs.loadQuiz()
	if err != nil {
		sendError(w, http.StatusInternalServerError, "读取题目失败")
		return
	}
	pub, err := qs.publicQuestions()
	if err != nil {
		sendError(w, http.StatusInternalServerError, "读取题目失败")
		return
	}
	sendJSON(w, http.StatusOK, map[string]interface{}{"success": true, "period": period, "data": pub})
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
		"clientId":     creds.ClientID,
		"clientSecret": creds.ClientSecret,
		"gpcBaseUrl":   qs.gpc.baseURL(),
		"redirectUri":  qs.redirectURI,
		"scope":        qs.scope,
	})
}

// POST /api/quiz/submit — validates answers, mints rewards for correct and
// not-yet-claimed questions, records the whole submission (answers + grading)
// under the current period, and returns a per-question result plus the user's
// new balance. A period may only be submitted once per user.
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
	period, questions, err := qs.loadQuiz()
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

	// One-time guard per period: the quiz for this period may only be submitted
	// once per user. If it already was, return the stored record so the client
	// can show the previous answers and grading without re-submitting.
	if m := qs.submissions[userID]; m != nil {
		if rec := m[period]; rec != nil {
			sendJSON(w, http.StatusOK, map[string]interface{}{
				"success":          false,
				"alreadySubmitted": true,
				"period":           period,
				"submission":       rec,
				"message":          "您已提交过本期问卷，无法重复提交",
			})
			return
		}
	}

	userClaims := qs.claims[userID]
	if userClaims == nil {
		userClaims = map[string]claimRecord{}
		qs.claims[userID] = userClaims
	}
	round := 0

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
		claimKey := period + "\x00" + q.ID
		if _, done := userClaims[claimKey]; done {
			results = append(results, qres{ID: q.ID, Correct: true, AlreadyClaimed: true})
			continue
		}
		if err := qs.gpc.MintCoins(userID, q.Reward, "答题奖励 "+q.ID); err != nil {
			results = append(results, qres{ID: q.ID, Correct: true})
			continue
		}
		userClaims[claimKey] = claimRecord{Amount: q.Reward, At: time.Now().UnixMilli()}
		round += q.Reward
		results = append(results, qres{ID: q.ID, Correct: true, Awarded: q.Reward})
	}

	// Persist the full submission so the user can review it later, then save
	// both ledgers.
	rec := &SubmissionRecord{
		SubmittedAt:  time.Now().UnixMilli(),
		TotalAwarded: round,
		Answers:      req.Answers,
		Results:      results,
	}
	if qs.submissions[userID] == nil {
		qs.submissions[userID] = map[string]*SubmissionRecord{}
	}
	qs.submissions[userID][period] = rec
	qs.saveClaims()
	qs.saveSubmissions()

	sendJSON(w, http.StatusOK, map[string]interface{}{
		"success":      true,
		"period":       period,
		"results":      results,
		"totalAwarded": round,
	})
}

// GET /api/quiz/status?token=... — reports whether the token's user has already
// submitted the *current* period, and (when they have) returns the stored
// submission so clients can show the answers and grading immediately on open,
// without any submit click.
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
	period, _, err := qs.loadQuiz()
	if err != nil {
		sendError(w, http.StatusInternalServerError, "读取题目失败")
		return
	}
	qs.mu.Lock()
	var rec *SubmissionRecord
	if m := qs.submissions[userID]; m != nil {
		rec = m[period]
	}
	qs.mu.Unlock()

	resp := map[string]interface{}{
		"success":   true,
		"period":    period,
		"submitted": rec != nil,
	}
	if rec != nil {
		resp["submission"] = rec
	}
	sendJSON(w, http.StatusOK, resp)
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
