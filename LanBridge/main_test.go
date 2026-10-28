package main

import (
	"bytes"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"net/http/httptest"
	"os"
	"path/filepath"
	"testing"
)

func setup(t *testing.T) *server {
	t.Helper()
	root := t.TempDir()
	for _, p := range []string{"incoming", "trash"} {
		if e := os.MkdirAll(filepath.Join(root, ".bridge", p), 0700); e != nil {
			t.Fatal(e)
		}
	}
	return &server{root: root, token: "test-secret", uploads: map[string]*upload{}}
}
func req(s *server, method, url string, b []byte, hash string) *httptest.ResponseRecorder {
	r := httptest.NewRequest(method, url, bytes.NewReader(b))
	r.Header.Set("X-Token", s.token)
	if hash != "" {
		r.Header.Set("X-SHA256", hash)
	}
	w := httptest.NewRecorder()
	s.api(w, r)
	return w
}
func TestUploadRetryAndCommit(t *testing.T) {
	s := setup(t)
	w := req(s, "POST", "/api/start?path=test.txt", []byte(`{"Size":3}`), "")
	if w.Code != 200 {
		t.Fatal(w.Body)
	}
	var v struct{ ID string }
	json.Unmarshal(w.Body.Bytes(), &v)
	u := s.uploads[v.ID]
	url := "/api/chunk?id=" + v.ID + "&offset=0"
	if w = req(s, "POST", url, []byte("abc"), "bad"); w.Code == 200 {
		t.Fatal("bad checksum accepted")
	}
	if u.Offset != 0 {
		t.Fatal("bad chunk changed offset")
	}
	h := sha256.Sum256([]byte("abc"))
	if w = req(s, "POST", url, []byte("abc"), hex.EncodeToString(h[:])); w.Code != 200 {
		t.Fatal(w.Body)
	}
	w = req(s, "POST", url, []byte("abc"), hex.EncodeToString(h[:]))
	if w.Code != 200 || u.Offset != 3 {
		t.Fatal("retry duplicated bytes")
	}
	if _, e := os.Stat(filepath.Join(s.root, "test.txt")); !os.IsNotExist(e) {
		t.Fatal("incomplete file exposed")
	}
	w = req(s, "POST", "/api/finish?id="+v.ID, nil, "")
	if w.Code != 200 {
		t.Fatal(w.Body)
	}
	b, _ := os.ReadFile(filepath.Join(s.root, "test.txt"))
	if string(b) != "abc" {
		t.Fatal("content mismatch")
	}
	w = req(s, "POST", "/api/start?path=test.txt", []byte(`{"Size":0}`), "")
	if w.Code == 200 {
		t.Fatal("overwrite permitted")
	}
}
func TestConfinementAndAuth(t *testing.T) {
	s := setup(t)
	for _, p := range []string{"../secret", "a/../../secret", ".bridge/trash", "a\\b", "C:/Windows", "CON.txt", "a/../b"} {
		if _, e := s.path(p); e == nil {
			t.Fatalf("accepted %q", p)
		}
	}
	outside := t.TempDir()
	if e := os.Symlink(outside, filepath.Join(s.root, "link")); e == nil {
		if _, e = s.path("link/file"); e == nil {
			t.Fatal("symlink escape")
		}
	}
	r := httptest.NewRequest("GET", "/api/list", nil)
	w := httptest.NewRecorder()
	s.api(w, r)
	if w.Code != 401 {
		t.Fatal("unauthenticated access")
	}
	r = httptest.NewRequest("GET", "/api/list", nil)
	r.Header.Set("X-Token", s.token)
	r.Header.Set("Origin", "http://evil.test")
	w = httptest.NewRecorder()
	s.api(w, r)
	if w.Code != 403 {
		t.Fatal("cross-origin accepted")
	}
}
func TestOperationsAndConflict(t *testing.T) {
	s := setup(t)
	os.WriteFile(filepath.Join(s.root, "a.txt"), []byte("old"), 0600)
	w := req(s, "POST", "/api/action?path=a.txt", []byte(`{"Op":"save","Text":"new","Hash":"stale"}`), "")
	if w.Code == 200 {
		t.Fatal("stale overwrite")
	}
	h, _ := hashFile(filepath.Join(s.root, "a.txt"))
	in, _ := json.Marshal(map[string]string{"Op": "save", "Text": "new", "Hash": h})
	w = req(s, "POST", "/api/action?path=a.txt", in, "")
	if w.Code != 200 {
		t.Fatal(w.Body)
	}
	w = req(s, "POST", "/api/action?path=a.txt", []byte(`{"Op":"copy","To":"b.txt"}`), "")
	if w.Code != 200 {
		t.Fatal(w.Body)
	}
	w = req(s, "POST", "/api/action?path=b.txt", []byte(`{"Op":"move","To":"c.txt"}`), "")
	if w.Code != 200 {
		t.Fatal(w.Body)
	}
	w = req(s, "POST", "/api/action?path=c.txt", []byte(`{"Op":"delete"}`), "")
	if w.Code != 200 {
		t.Fatal(w.Body)
	}
	items, _ := os.ReadDir(filepath.Join(s.root, ".bridge", "trash"))
	if len(items) != 2 {
		t.Fatal("backup lost")
	}
	found := false
	for _, i := range items {
		b, _ := os.ReadFile(filepath.Join(s.root, ".bridge", "trash", i.Name()))
		if string(b) == "old" {
			found = true
		}
	}
	if !found {
		t.Fatal("original missing")
	}
}
func TestDownloadTicket(t *testing.T) {
	s := setup(t)
	os.WriteFile(filepath.Join(s.root, "a.txt"), []byte("abc"), 0600)
	w := req(s, "POST", "/api/ticket?path=a.txt", nil, "")
	var v map[string]string
	json.Unmarshal(w.Body.Bytes(), &v)
	if w.Code != 200 {
		t.Fatal(w.Body)
	}
	r := httptest.NewRequest("GET", v["url"], nil)
	w = httptest.NewRecorder()
	s.api(w, r)
	if w.Code != 200 || w.Body.String() != "abc" {
		t.Fatal("ticket failed")
	}
	r = httptest.NewRequest("GET", v["url"]+"x", nil)
	w = httptest.NewRecorder()
	s.api(w, r)
	if w.Code != 401 {
		t.Fatal("tampered ticket accepted")
	}
}
