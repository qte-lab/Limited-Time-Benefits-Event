package main

import (
	"encoding/json"
	"os"
	"path/filepath"
	"sync"
	"time"
)

type cachedData struct {
	data      interface{}
	timestamp time.Time
}

type Cache struct {
	mu      sync.RWMutex
	items   map[string]cachedData
	duration time.Duration
}

func NewCache(duration time.Duration) *Cache {
	return &Cache{
		items:   make(map[string]cachedData),
		duration: duration,
	}
}

func (c *Cache) Get(key string) (interface{}, bool) {
	c.mu.RLock()
	defer c.mu.RUnlock()
	
	item, exists := c.items[key]
	if !exists {
		return nil, false
	}
	
	if time.Since(item.timestamp) > c.duration {
		return nil, false
	}
	
	return item.data, true
}

func (c *Cache) Set(key string, data interface{}) {
	c.mu.Lock()
	defer c.mu.Unlock()
	
	c.items[key] = cachedData{
		data:      data,
		timestamp: time.Now(),
	}
}

func readJSONFile(filePath string, v interface{}) error {
	content, err := os.ReadFile(filePath)
	if err != nil {
		return err
	}
	return json.Unmarshal(content, v)
}

func getActivities(dataDir string, cache *Cache) ([]interface{}, error) {
	if cached, ok := cache.Get("activities"); ok {
		return cached.([]interface{}), nil
	}
	
	var result struct {
		Activities []interface{} `json:"activities"`
	}
	
	err := readJSONFile(filepath.Join(dataDir, "activities.json"), &result)
	if err != nil {
		return nil, err
	}
	
	if result.Activities == nil {
		result.Activities = []interface{}{}
	}
	
	cache.Set("activities", result.Activities)
	return result.Activities, nil
}

func getChangelog(dataDir string, cache *Cache) (map[string]interface{}, error) {
	if cached, ok := cache.Get("changelog"); ok {
		return cached.(map[string]interface{}), nil
	}
	
	var result struct {
		Changelog map[string]interface{} `json:"changelog"`
	}
	
	err := readJSONFile(filepath.Join(dataDir, "changelog.json"), &result)
	if err != nil {
		return nil, err
	}
	
	if result.Changelog == nil {
		result.Changelog = make(map[string]interface{})
	}
	
	cache.Set("changelog", result.Changelog)
	return result.Changelog, nil
}