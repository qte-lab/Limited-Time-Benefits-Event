package main

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net/http"
	"os"
	"path/filepath"
	"sync"
	"time"
)

// GpcConfig holds the connection info the event server needs to talk to the
// Gold-Pig-Coin (GPC) server: where it lives, admin credentials used to mint
// rewards, and the OAuth client that the Android app uses to authorize.
type GpcConfig struct {
	BaseURL         string `json:"baseUrl"`
	AdminUser       string `json:"adminUser"`
	AdminPass       string `json:"adminPass"`
	OAuthClientName string `json:"oauthClientName"`
	OAuthScope      string `json:"oauthScope"`
	RedirectURI     string `json:"redirectUri"`
	DailyLimit      int    `json:"dailyLimitPerUser"`
}

// OAuthClientCreds is the client id/secret registered with GPC. The secret is
// generated once and persisted locally so the Android app can fetch it at
// runtime (see /api/oauth/gpc-config).
type OAuthClientCreds struct {
	ClientID     string `json:"clientId"`
	ClientSecret string `json:"clientSecret"`
}

type gpcClient struct {
	cfg            GpcConfig
	httpClient     *http.Client
	oauthCreds     OAuthClientCreds
	oauthCredsPath string

	adminToken string
	adminExp   time.Time

	// mu protects the OAuth client credentials (in-memory + file).
	mu sync.Mutex
	// tokMu protects the cached admin token only. It is intentionally a
	// separate lock from mu: ensureOAuthClient() holds mu while calling
	// getAdminToken(), so the two must not alias or we deadlock.
	tokMu sync.Mutex
}

// gpcApiResponse mirrors the unified { success, data, message } envelope used
// by every GPC endpoint.
type gpcApiResponse struct {
	Success bool            `json:"success"`
	Data    json.RawMessage `json:"data"`
	Message string          `json:"message"`
}

func newGpcClient(cfg GpcConfig, dataDir string) *gpcClient {
	return &gpcClient{
		cfg:            cfg,
		httpClient:     &http.Client{Timeout: 15 * time.Second},
		oauthCredsPath: filepath.Join(dataDir, "gpc_oauth_client.json"),
	}
}

// baseURL prefers the GPC_BASE_URL env override (handy for local testing) and
// falls back to the value from gpc_config.json.
func (g *gpcClient) baseURL() string {
	if v := os.Getenv("GPC_BASE_URL"); v != "" {
		return v
	}
	return g.cfg.BaseURL
}

func (g *gpcClient) doJSON(method, apiPath string, body interface{}, token string) (*gpcApiResponse, error) {
	var reader io.Reader
	if body != nil {
		buf, err := json.Marshal(body)
		if err != nil {
			return nil, err
		}
		reader = bytes.NewReader(buf)
	}
	req, err := http.NewRequest(method, g.baseURL()+"/api"+apiPath, reader)
	if err != nil {
		return nil, err
	}
	req.Header.Set("Content-Type", "application/json")
	if token != "" {
		req.Header.Set("Authorization", "Bearer "+token)
	}
	resp, err := g.httpClient.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	data, _ := io.ReadAll(resp.Body)
	var out gpcApiResponse
	_ = json.Unmarshal(data, &out)
	return &out, nil
}

func (g *gpcClient) adminLogin() (string, error) {
	r, err := g.doJSON(http.MethodPost, "/auth/login", map[string]string{
		"username": g.cfg.AdminUser,
		"password": g.cfg.AdminPass,
	}, "")
	if err != nil {
		return "", err
	}
	if !r.Success {
		return "", fmt.Errorf("GPC 管理员登录失败: %s", r.Message)
	}
	var d struct {
		Token string `json:"token"`
	}
	if err := json.Unmarshal(r.Data, &d); err != nil {
		return "", err
	}
	return d.Token, nil
}

// getAdminToken returns a cached admin token, logging in (and caching) a fresh
// one when missing or expired. Uses tokMu so concurrent callers don't all log
// in at once; it does NOT hold mu, so callers that already hold mu are safe.
func (g *gpcClient) getAdminToken() (string, error) {
	g.tokMu.Lock()
	defer g.tokMu.Unlock()
	if g.adminToken != "" && time.Now().Before(g.adminExp) {
		return g.adminToken, nil
	}
	tok, err := g.adminLogin()
	if err != nil {
		return "", err
	}
	g.adminToken = tok
	g.adminExp = time.Now().Add(30 * time.Minute)
	log.Println("[gpc] admin token refreshed")
	return tok, nil
}

// ensureOAuthClient returns the registered OAuth client, creating it on the
// GPC server (and persisting the secret) the first time, and reusing the
// persisted credentials afterwards. Holds mu (never tokMu) while doing so.
func (g *gpcClient) ensureOAuthClient() (OAuthClientCreds, error) {
	g.mu.Lock()
	defer g.mu.Unlock()

	if g.oauthCreds.ClientID != "" {
		return g.oauthCreds, nil
	}
	if data, err := os.ReadFile(g.oauthCredsPath); err == nil {
		var c OAuthClientCreds
		if json.Unmarshal(data, &c) == nil && c.ClientID != "" {
			g.oauthCreds = c
			return c, nil
		}
	}

	log.Println("[gpc] registering OAuth client on GPC...")
	tok, err := g.getAdminToken()
	if err != nil {
		return OAuthClientCreds{}, fmt.Errorf("获取管理员令牌失败: %w", err)
	}
	r, err := g.doJSON(http.MethodPost, "/admin/oauth-client", map[string]interface{}{
		"name":         g.cfg.OAuthClientName,
		"redirectUris": []string{g.cfg.RedirectURI},
		"scopes":       []string{g.cfg.OAuthScope},
	}, tok)
	if err != nil {
		return OAuthClientCreds{}, fmt.Errorf("调用 GPC 创建客户端失败: %w", err)
	}
	if !r.Success {
		return OAuthClientCreds{}, fmt.Errorf("创建 GPC OAuth 客户端失败: %s", r.Message)
	}
	var d struct {
		ClientID     string `json:"clientId"`
		ClientSecret string `json:"clientSecret"`
	}
	if err := json.Unmarshal(r.Data, &d); err != nil {
		return OAuthClientCreds{}, fmt.Errorf("解析 GPC 客户端响应失败: %w", err)
	}
	c := OAuthClientCreds{ClientID: d.ClientID, ClientSecret: d.ClientSecret}
	g.oauthCreds = c
	if b, err := json.MarshalIndent(c, "", "  "); err == nil {
		_ = os.WriteFile(g.oauthCredsPath, b, 0644)
	}
	log.Println("[gpc] OAuth client ready:", c.ClientID)
	return c, nil
}

// MintCoins credits reward GPC to a user via the GPC admin "币种发放" endpoint.
func (g *gpcClient) MintCoins(userID string, amount int, note string) error {
	tok, err := g.getAdminToken()
	if err != nil {
		return err
	}
	r, err := g.doJSON(http.MethodPost, "/admin/coins/issue", map[string]interface{}{
		"userId": userID,
		"amount": amount,
		"note":   note,
	}, tok)
	if err != nil {
		return err
	}
	if !r.Success {
		return fmt.Errorf("发放失败: %s", r.Message)
	}
	return nil
}

// VerifyToken validates a GPC access token and returns the owning user id.
func (g *gpcClient) VerifyToken(token string) (string, error) {
	r, err := g.doJSON(http.MethodGet, "/auth/me", nil, token)
	if err != nil {
		return "", err
	}
	if !r.Success {
		return "", fmt.Errorf("token 校验失败: %s", r.Message)
	}
	var d struct {
		ID string `json:"id"`
	}
	if err := json.Unmarshal(r.Data, &d); err != nil {
		return "", err
	}
	return d.ID, nil
}

// GetBalance returns the user's GPC balance for the results screen.
func (g *gpcClient) GetBalance(token string) (int, error) {
	r, err := g.doJSON(http.MethodGet, "/auth/me", nil, token)
	if err != nil {
		return 0, err
	}
	var d struct {
		Balance int `json:"balance"`
	}
	if err := json.Unmarshal(r.Data, &d); err != nil {
		return 0, err
	}
	return d.Balance, nil
}
