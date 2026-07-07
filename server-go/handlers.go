package main

import (
	"encoding/json"
	"fmt"
	"net/http"
	"os"
	"path/filepath"
	"strconv"
	"strings"
)

type HandlerConfig struct {
	dataDir string
	apkDir  string
	cache   *Cache
}

func NewHandlerConfig(dataDir, apkDir string, cache *Cache) *HandlerConfig {
	return &HandlerConfig{
		dataDir: dataDir,
		apkDir:  apkDir,
		cache:   cache,
	}
}

func sendJSON(w http.ResponseWriter, status int, data interface{}) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	json.NewEncoder(w).Encode(data)
}

func sendError(w http.ResponseWriter, status int, message string) {
	sendJSON(w, status, map[string]interface{}{
		"success": false,
		"message": message,
	})
}

func (h *HandlerConfig) activitiesHandler(w http.ResponseWriter, r *http.Request) {
	activities, err := getActivities(h.dataDir, h.cache)
	if err != nil {
		sendError(w, http.StatusInternalServerError, "Failed to read activities data")
		return
	}
	
	sendJSON(w, http.StatusOK, map[string]interface{}{
		"success": true,
		"data":    activities,
	})
}

func (h *HandlerConfig) downloadApkHandler(w http.ResponseWriter, r *http.Request) {
	files, err := os.ReadDir(h.apkDir)
	if err != nil {
		sendError(w, http.StatusInternalServerError, "Failed to read APK directory")
		return
	}
	
	var apks []string
	for _, file := range files {
		if !file.IsDir() && strings.HasSuffix(file.Name(), ".apk") {
			apks = append(apks, file.Name())
		}
	}
	
	var latestApk string = "app-release.apk"
	var latestApkSize string
	var versionCode int
	var versionName string = "unknown"
	
	metaPath := filepath.Join(h.apkDir, "output-metadata.json")
	if metaContent, err := os.ReadFile(metaPath); err == nil {
		var metaData struct {
			Elements []struct {
				OutputFile  string `json:"outputFile"`
				VersionCode int    `json:"versionCode"`
				VersionName string `json:"versionName"`
			} `json:"elements"`
		}
		
		if json.Unmarshal(metaContent, &metaData) == nil && len(metaData.Elements) > 0 {
			elem := metaData.Elements[0]
			if elem.OutputFile != "" {
				latestApk = elem.OutputFile
			}
			versionCode = elem.VersionCode
			if elem.VersionName != "" {
				versionName = elem.VersionName
			}
		}
	}
	
	if latestApk != "" {
		if stats, err := os.Stat(filepath.Join(h.apkDir, latestApk)); err == nil {
			sizeMB := float64(stats.Size()) / (1024 * 1024)
			latestApkSize = fmt.Sprintf("%.1f", sizeMB)
		}
	}
	
	changelog, err := getChangelog(h.dataDir, h.cache)
	if err != nil {
		changelog = make(map[string]interface{})
	}
	
	sendJSON(w, http.StatusOK, map[string]interface{}{
		"success":     true,
		"data":        apks,
		"latest":      latestApk,
		"latestSize":  latestApkSize,
		"versionCode": versionCode,
		"versionName": versionName,
		"changelog":   changelog,
	})
}

func (h *HandlerConfig) downloadApkFileHandler(w http.ResponseWriter, r *http.Request) {
	filename := r.PathValue("filename")
	
	if !strings.HasSuffix(filename, ".apk") {
		sendError(w, http.StatusBadRequest, "Invalid file type")
		return
	}
	
	apkPath := filepath.Join(h.apkDir, filename)
	
	file, err := os.Open(apkPath)
	if err != nil {
		if os.IsNotExist(err) {
			sendError(w, http.StatusNotFound, "File not found")
		} else {
			sendError(w, http.StatusInternalServerError, "Failed to read APK file")
		}
		return
	}
	defer file.Close()
	
	stats, err := file.Stat()
	if err != nil {
		sendError(w, http.StatusInternalServerError, "Failed to get file info")
		return
	}
	
	w.Header().Set("Content-Type", "application/vnd.android.package-archive")
	w.Header().Set("Content-Disposition", fmt.Sprintf("attachment; filename=\"%s\"", filename))
	w.Header().Set("Content-Length", strconv.FormatInt(stats.Size(), 10))
	w.Header().Set("Cache-Control", "no-cache")
	
	http.ServeContent(w, r, filename, stats.ModTime(), file)
}

func (h *HandlerConfig) listMarkdownHandler(w http.ResponseWriter, r *http.Request) {
	markdownDir := filepath.Join(h.dataDir, "outdate-test-markdown")
	
	files, err := os.ReadDir(markdownDir)
	if err != nil {
		sendError(w, http.StatusInternalServerError, "Failed to read markdown files directory")
		return
	}
	
	var markdownFiles []string
	for _, file := range files {
		if !file.IsDir() && strings.HasSuffix(file.Name(), ".md") {
			markdownFiles = append(markdownFiles, file.Name())
		}
	}
	
	for i := 0; i < len(markdownFiles)/2; i++ {
		j := len(markdownFiles) - 1 - i
		markdownFiles[i], markdownFiles[j] = markdownFiles[j], markdownFiles[i]
	}
	
	sendJSON(w, http.StatusOK, map[string]interface{}{
		"success": true,
		"data":    markdownFiles,
	})
}

func (h *HandlerConfig) getMarkdownFileHandler(w http.ResponseWriter, r *http.Request) {
	filename := r.PathValue("filename")
	
	if !strings.HasSuffix(filename, ".md") {
		http.NotFound(w, r)
		return
	}
	
	filePath := filepath.Join(h.dataDir, "outdate-test-markdown", filename)
	
	content, err := os.ReadFile(filePath)
	if err != nil {
		if os.IsNotExist(err) {
			sendError(w, http.StatusNotFound, "File not found")
		} else {
			sendError(w, http.StatusInternalServerError, "Failed to read markdown file")
		}
		return
	}
	
	sendJSON(w, http.StatusOK, map[string]interface{}{
		"success": true,
		"data": map[string]string{
			"filename": filename,
			"content":  string(content),
		},
	})
}