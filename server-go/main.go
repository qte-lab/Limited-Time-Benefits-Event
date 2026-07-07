package main

import (
	"compress/gzip"
	"io"
	"log"
	"net/http"
	"os"
	"path/filepath"
	"strings"
	"time"
)

type gzipResponseWriter struct {
	http.ResponseWriter
	Writer io.Writer
}

func (w *gzipResponseWriter) Write(b []byte) (int, error) {
	return w.Writer.Write(b)
}

func gzipHandler(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if !strings.Contains(r.Header.Get("Accept-Encoding"), "gzip") {
			next.ServeHTTP(w, r)
			return
		}

		w.Header().Set("Content-Encoding", "gzip")
		gz := gzip.NewWriter(w)
		defer gz.Close()

		gzWriter := &gzipResponseWriter{ResponseWriter: w, Writer: gz}
		next.ServeHTTP(gzWriter, r)
	})
}

func main() {
	serverDir, err := os.Getwd()
	if err != nil {
		log.Fatal("Failed to get working directory:", err)
	}

	dataDir := filepath.Join(serverDir, "..", "server", "data")
	apkDir := filepath.Join(serverDir, "..", "server", "apk")

	cache := NewCache(60 * time.Second)
	handlerConfig := NewHandlerConfig(dataDir, apkDir, cache)

	mux := http.NewServeMux()

	mux.HandleFunc("/api/activities", handlerConfig.activitiesHandler)
	mux.HandleFunc("/api/download_apk", handlerConfig.downloadApkHandler)
	mux.HandleFunc("/api/download_apk/{filename}", handlerConfig.downloadApkFileHandler)
	mux.HandleFunc("/api/outdate-test/markdown", handlerConfig.listMarkdownHandler)
	mux.HandleFunc("/api/outdate-test/markdown/{filename}", handlerConfig.getMarkdownFileHandler)

	rootDir := filepath.Join(serverDir, "..")
	fileServer := http.FileServer(http.Dir(rootDir))

	mux.Handle("/", http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if strings.HasSuffix(r.URL.Path, ".flac") {
			w.Header().Set("Content-Type", "audio/flac")
		}
		fileServer.ServeHTTP(w, r)
	}))

	port := os.Getenv("PORT")
	if port == "" {
		port = "3001"
	}

	wrappedHandler := gzipHandler(mux)

	log.Printf("Server started, accessible at: http://0.0.0.0:%s", port)
	log.Printf("Local access: http://localhost:%s", port)

	log.Fatal(http.ListenAndServe("0.0.0.0:"+port, wrappedHandler))
}