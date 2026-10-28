package main

import (
	"crypto/hmac"
	"crypto/rand"
	"crypto/sha256"
	"crypto/subtle"
	"embed"
	"encoding/hex"
	"encoding/json"
	"errors"
	"flag"
	"fmt"
	"io"
	"io/fs"
	"log"
	"net"
	"net/http"
	"net/url"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"strconv"
	"strings"
	"sync"
	"time"
	"unicode/utf8"
)

//go:embed web/*
var web embed.FS

type upload struct {
	Path         string
	Size, Offset int64
	Temp         string
}
type server struct {
	root    string
	token   string
	mu      sync.Mutex
	uploads map[string]*upload
}

func uid() string {
	b := make([]byte, 24)
	if _, e := rand.Read(b); e != nil {
		panic(e)
	}
	return hex.EncodeToString(b)
}
func (s *server) path(p string) (string, error) {
	if strings.ContainsAny(p, "\\:\x00<>\"|?*") || strings.HasPrefix(p, "/") {
		return "", errors.New("无效路径")
	}
	parts := strings.Split(p, "/")
	out := s.root
	for _, v := range parts {
		if v == "" {
			continue
		}
		if v == "." || v == ".." || strings.HasPrefix(v, ".") || strings.TrimRight(v, " .") != v {
			return "", errors.New("禁止访问此路径")
		}
		base := strings.ToUpper(strings.Split(v, ".")[0])
		if base == "CON" || base == "PRN" || base == "AUX" || base == "NUL" || (len(base) == 4 && (strings.HasPrefix(base, "COM") || strings.HasPrefix(base, "LPT")) && base[3] >= '0' && base[3] <= '9') {
			return "", errors.New("Windows 保留名称")
		}
		out = filepath.Join(out, v)
		st, e := os.Lstat(out)
		if e == nil && st.Mode()&os.ModeSymlink != 0 {
			return "", errors.New("不允许符号链接")
		}
		if e != nil && !os.IsNotExist(e) {
			return "", e
		}
	}
	return out, nil
}
func bytesContainNull(b []byte) bool {
	for _, v := range b {
		if v == 0 {
			return true
		}
	}
	return false
}
func hashFile(p string) (string, error) {
	f, e := os.Open(p)
	if e != nil {
		return "", e
	}
	defer f.Close()
	h := sha256.New()
	_, e = io.Copy(h, f)
	return hex.EncodeToString(h.Sum(nil)), e
}
func jsonOut(w http.ResponseWriter, v any) {
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(v)
}
func (s *server) signature(path, expires string) string {
	h := hmac.New(sha256.New, []byte(s.token))
	h.Write([]byte(path + "\n" + expires))
	return hex.EncodeToString(h.Sum(nil))
}
func (s *server) api(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Cache-Control", "no-store")
	w.Header().Set("X-Content-Type-Options", "nosniff")
	authorized := subtle.ConstantTimeCompare([]byte(r.Header.Get("X-Token")), []byte(s.token)) == 1
	if !authorized && r.URL.Path == "/api/download" && r.Method == "GET" {
		exp := r.URL.Query().Get("expires")
		n, e := strconv.ParseInt(exp, 10, 64)
		authorized = e == nil && n >= time.Now().Unix() && n <= time.Now().Unix()+120 && hmac.Equal([]byte(r.URL.Query().Get("sig")), []byte(s.signature(r.URL.Query().Get("path"), exp)))
	}
	if !authorized {
		http.Error(w, "连接口令不正确", 401)
		return
	}
	if r.Header.Get("Origin") != "" && r.Header.Get("Origin") != "http://"+r.Host {
		http.Error(w, "来源不允许", 403)
		return
	}
	s.mu.Lock()
	defer s.mu.Unlock()
	var err error
	defer func() {
		if err != nil {
			http.Error(w, err.Error(), 400)
		}
	}()
	p, e := s.path(r.URL.Query().Get("path"))
	if e != nil {
		err = e
		return
	}
	switch r.URL.Path {
	case "/api/ticket":
		if r.Method != "POST" {
			err = errors.New("请求方法错误")
			return
		}
		exp := strconv.FormatInt(time.Now().Unix()+120, 10)
		name := r.URL.Query().Get("path")
		jsonOut(w, map[string]string{"url": "/api/download?path=" + url.QueryEscape(name) + "&expires=" + exp + "&sig=" + s.signature(name, exp)})
	case "/api/list":
		if r.Method != "GET" {
			err = errors.New("请求方法错误")
			return
		}
		entries, e := os.ReadDir(p)
		if e != nil {
			err = e
			return
		}
		rows := []map[string]any{}
		for _, d := range entries {
			if strings.HasPrefix(d.Name(), ".") || d.Type()&os.ModeSymlink != 0 {
				continue
			}
			st, e := d.Info()
			if e != nil {
				continue
			}
			rows = append(rows, map[string]any{"name": d.Name(), "dir": d.IsDir(), "size": st.Size(), "modified": st.ModTime().Format(time.RFC3339)})
		}
		jsonOut(w, rows)
	case "/api/download":
		if r.Method != "GET" {
			err = errors.New("请求方法错误")
			return
		}
		st, e := os.Stat(p)
		if e != nil {
			err = e
			return
		}
		if !st.Mode().IsRegular() {
			err = errors.New("请选择文件")
			return
		}
		w.Header().Set("Content-Disposition", "attachment; filename*=UTF-8''"+escapeName(filepath.Base(p)))
		http.ServeFile(w, r, p)
	case "/api/text":
		if r.Method != "GET" {
			err = errors.New("请求方法错误")
			return
		}
		st, e := os.Stat(p)
		if e != nil {
			err = e
			return
		}
		if !st.Mode().IsRegular() || st.Size() > 2<<20 {
			err = errors.New("文本预览仅支持 2MB 以内的文件")
			return
		}
		b, e := os.ReadFile(p)
		if e != nil {
			err = e
			return
		}
		if !utf8.Valid(b) || bytesContainNull(b) {
			err = errors.New("该文件不是 UTF-8 文本，请下载后使用对应应用打开")
			return
		}
		h := sha256.Sum256(b)
		jsonOut(w, map[string]any{"text": string(b), "hash": hex.EncodeToString(h[:])})
	case "/api/start":
		if r.Method != "POST" {
			err = errors.New("请求方法错误")
			return
		}
		var in struct{ Size int64 }
		if e := json.NewDecoder(http.MaxBytesReader(w, r.Body, 4096)).Decode(&in); e != nil {
			err = e
			return
		}
		if p == s.root || in.Size < 0 {
			err = errors.New("文件参数错误")
			return
		}
		if _, e = os.Lstat(p); !os.IsNotExist(e) {
			err = errors.New("目标已存在，请先改名")
			return
		}
		if st, e := os.Stat(filepath.Dir(p)); e != nil || !st.IsDir() {
			err = errors.New("父目录不存在")
			return
		}
		id := uid()
		tmp := filepath.Join(s.root, ".bridge", "incoming", id)
		f, e := os.OpenFile(tmp, os.O_CREATE|os.O_EXCL|os.O_WRONLY, 0600)
		if e != nil {
			err = e
			return
		}
		f.Close()
		s.uploads[id] = &upload{Path: p, Size: in.Size, Temp: tmp}
		jsonOut(w, map[string]any{"id": id, "offset": 0})
	case "/api/chunk":
		if r.Method != "POST" {
			err = errors.New("请求方法错误")
			return
		}
		u := s.uploads[r.URL.Query().Get("id")]
		if u == nil {
			err = errors.New("传输已失效，请重新上传")
			return
		}
		off, e := strconv.ParseInt(r.URL.Query().Get("offset"), 10, 64)
		if e != nil {
			err = e
			return
		}
		if off != u.Offset {
			jsonOut(w, map[string]any{"offset": u.Offset})
			return
		}
		b, e := io.ReadAll(http.MaxBytesReader(w, r.Body, 4<<20))
		if e != nil {
			err = e
			return
		}
		h := sha256.Sum256(b)
		if hex.EncodeToString(h[:]) != r.Header.Get("X-SHA256") {
			err = errors.New("分块校验失败")
			return
		}
		if u.Offset+int64(len(b)) > u.Size {
			err = errors.New("文件长度超出声明")
			return
		}
		f, e := os.OpenFile(u.Temp, os.O_WRONLY, 0600)
		if e != nil {
			err = e
			return
		}
		n, e := f.WriteAt(b, u.Offset)
		if e == nil {
			e = f.Sync()
		}
		f.Close()
		if e != nil {
			err = e
			return
		}
		u.Offset += int64(n)
		jsonOut(w, map[string]any{"offset": u.Offset})
	case "/api/finish":
		if r.Method != "POST" {
			err = errors.New("请求方法错误")
			return
		}
		id := r.URL.Query().Get("id")
		u := s.uploads[id]
		if u == nil || u.Offset != u.Size {
			err = errors.New("文件未传完")
			return
		}
		rel, e := filepath.Rel(s.root, u.Path)
		if e != nil {
			err = e
			return
		}
		if _, e = s.path(filepath.ToSlash(rel)); e != nil {
			err = e
			return
		}
		h, e := hashFile(u.Temp)
		if e != nil {
			err = e
			return
		}
		if e = os.Link(u.Temp, u.Path); e != nil {
			err = fmt.Errorf("安全提交失败（目标可能已存在，或磁盘不支持硬链接）：%w", e)
			return
		}
		os.Remove(u.Temp)
		delete(s.uploads, id)
		jsonOut(w, map[string]any{"sha256": h, "size": u.Size})
	case "/api/action":
		if r.Method != "POST" {
			err = errors.New("请求方法错误")
			return
		}
		var in struct{ Op, To, Text, Hash string }
		if e := json.NewDecoder(http.MaxBytesReader(w, r.Body, 3<<20)).Decode(&in); e != nil {
			err = e
			return
		}
		if p == s.root {
			err = errors.New("不能修改共享根目录")
			return
		}
		switch in.Op {
		case "mkdir":
			err = os.Mkdir(p, 0700)
		case "delete":
			err = os.Rename(p, filepath.Join(s.root, ".bridge", "trash", uid()+"-"+filepath.Base(p)))
		case "move", "copy":
			dst, e := s.path(in.To)
			if e != nil {
				err = e
				return
			}
			if _, e = os.Lstat(dst); !os.IsNotExist(e) {
				err = errors.New("目标已存在")
				return
			}
			if in.Op == "move" {
				err = os.Rename(p, dst)
			} else {
				st, e := os.Stat(p)
				if e != nil {
					err = e
					return
				}
				if !st.Mode().IsRegular() {
					err = errors.New("复制仅支持文件，目录可移动")
					return
				}
				tmp := filepath.Join(s.root, ".bridge", "incoming", uid())
				if e = copyFile(p, tmp); e != nil {
					err = e
					return
				}
				defer os.Remove(tmp)
				err = os.Link(tmp, dst)
			}
		case "save":
			st, e := os.Stat(p)
			if e != nil || !st.Mode().IsRegular() || st.Size() > 2<<20 {
				err = errors.New("只能编辑 2MB 以内文本")
				return
			}
			h, e := hashFile(p)
			if e != nil {
				err = e
				return
			}
			if h != in.Hash {
				err = errors.New("文件已被其他操作修改，请重新打开")
				return
			}
			backup := filepath.Join(s.root, ".bridge", "trash", uid()+"-"+filepath.Base(p))
			if e = copyFile(p, backup); e != nil {
				err = e
				return
			}
			tmp := filepath.Join(s.root, ".bridge", "incoming", uid())
			f, e := os.OpenFile(tmp, os.O_WRONLY|os.O_CREATE|os.O_EXCL, 0600)
			if e != nil {
				err = e
				return
			}
			_, e = f.WriteString(in.Text)
			if e == nil {
				e = f.Sync()
			}
			f.Close()
			defer os.Remove(tmp)
			if e != nil {
				err = e
				return
			}
			err = os.Rename(tmp, p)
		default:
			err = errors.New("未知操作")
		}
		if err == nil {
			jsonOut(w, map[string]bool{"ok": true})
		}
	default:
		http.NotFound(w, r)
	}
}
func escapeName(s string) string {
	var b strings.Builder
	for _, c := range []byte(s) {
		fmt.Fprintf(&b, "%%%02X", c)
	}
	return b.String()
}
func copyFile(src, dst string) error {
	f, e := os.Open(src)
	if e != nil {
		return e
	}
	defer f.Close()
	o, e := os.OpenFile(dst, os.O_CREATE|os.O_EXCL|os.O_WRONLY, 0600)
	if e != nil {
		return e
	}
	_, e = io.Copy(o, f)
	if e == nil {
		e = o.Sync()
	}
	ce := o.Close()
	if e != nil {
		os.Remove(dst)
		return e
	}
	return ce
}
func main() {
	home, _ := os.UserHomeDir()
	root := flag.String("root", filepath.Join(home, "LanBridge共享"), "专用共享目录（建议 NTFS）")
	port := flag.Int("port", 8765, "监听端口")
	noBrowser := flag.Bool("no-browser", false, "不打开浏览器")
	flag.Parse()
	abs, e := filepath.Abs(*root)
	if e != nil {
		log.Fatal(e)
	}
	if e = os.MkdirAll(abs, 0700); e != nil {
		log.Fatal(e)
	}
	abs, e = filepath.EvalSymlinks(abs)
	if e != nil {
		log.Fatal(e)
	}
	for _, p := range []string{".bridge", ".bridge/incoming", ".bridge/trash"} {
		dest := filepath.Join(abs, p)
		if st, e := os.Lstat(dest); e == nil && st.Mode()&os.ModeSymlink != 0 {
			log.Fatal("内部目录不允许链接")
		}
		if e = os.MkdirAll(dest, 0700); e != nil {
			log.Fatal(e)
		}
	}
	s := &server{root: abs, token: uid(), uploads: map[string]*upload{}}
	mux := http.NewServeMux()
	mux.HandleFunc("/api/", s.api)
	sub, _ := fs.Sub(web, "web")
	mux.Handle("/", http.FileServer(http.FS(sub)))
	ln, e := net.Listen("tcp", fmt.Sprintf(":%d", *port))
	if e != nil {
		log.Fatal(e)
	}
	url := fmt.Sprintf("http://127.0.0.1:%d/#%s", *port, s.token)
	fmt.Printf("\nLanBridge 局域网文件共享\n共享目录: %s\n电脑访问: %s\n", abs, url)
	addrs, _ := net.InterfaceAddrs()
	for _, a := range addrs {
		if ip, n, err := net.ParseCIDR(a.String()); err == nil && n != nil && ip.To4() != nil && !ip.IsLoopback() {
			fmt.Printf("手机/iPad: http://%s:%d/#%s\n", ip, *port, s.token)
		}
	}
	fmt.Println("同一可信 Wi-Fi 下访问。关闭本窗口即停止共享。删除文件及编辑前版本在共享目录 .bridge/trash 中保留。")
	if !*noBrowser && runtime.GOOS == "windows" {
		exec.Command("rundll32", "url.dll,FileProtocolHandler", url).Start()
	}
	srv := &http.Server{Handler: mux, ReadHeaderTimeout: 10 * time.Second, IdleTimeout: 90 * time.Second, MaxHeaderBytes: 16 << 10}
	log.Fatal(srv.Serve(ln))
}
