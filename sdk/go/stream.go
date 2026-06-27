package auvex

import (
	"bufio"
	"context"
	"encoding/json"
	"io"
	"net/http"
	"strings"
)

// Stream is an iterator over the parsed chunks of a streaming chat completion.
//
// The gateway sends Server-Sent Events as "data: {json}" lines terminated by a final
// "data: [DONE]" sentinel. Drive the iterator with Next, read the current chunk with
// Current, and check Err once it finishes. Always Close it to release the connection:
//
//	stream, err := client.Chat.Completions.CreateStream(ctx, req)
//	if err != nil {
//		return err
//	}
//	defer stream.Close()
//	for stream.Next() {
//		chunk := stream.Current() // raw JSON for one OpenAI-style chunk
//		// decode and use chunk...
//	}
//	return stream.Err()
type Stream struct {
	resp    *http.Response
	reader  *bufio.Reader
	current json.RawMessage
	err     error
	done    bool
}

// stream POSTs a streaming request and returns a Stream once the gateway has accepted
// it. A non-2xx status is drained and reported as a typed *APIError instead.
func (c *Client) stream(ctx context.Context, path string, body any) (*Stream, error) {
	resp, err := c.doRequest(ctx, http.MethodPost, path, body, nil)
	if err != nil {
		return nil, err
	}
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		raw, _ := io.ReadAll(resp.Body)
		resp.Body.Close()
		return nil, errorFromResponse(resp.StatusCode, decodeBody(raw))
	}
	return &Stream{resp: resp, reader: bufio.NewReader(resp.Body)}, nil
}

// Next advances to the next chunk, returning true when one is available. It returns
// false at the end of the stream (including the [DONE] sentinel) or on error; check Err
// afterwards to tell the two apart. Blank lines and unparseable chunks are skipped.
func (s *Stream) Next() bool {
	if s.done || s.err != nil {
		return false
	}
	for {
		line, readErr := s.reader.ReadString('\n')
		if len(line) > 0 {
			payload, kind := parseSSELine(line)
			switch kind {
			case sseDone:
				s.done = true
				return false
			case sseData:
				s.current = payload
				return true
			}
			// sseSkip falls through to the error check below.
		}
		if readErr != nil {
			if readErr != io.EOF {
				s.err = mapTransportError(readErr)
			}
			s.done = true
			return false
		}
	}
}

// Current returns the most recent chunk yielded by Next, as the raw JSON for one
// OpenAI-style streaming chunk. Decode it into whatever shape you expect.
func (s *Stream) Current() json.RawMessage {
	return s.current
}

// Err returns the first non-EOF error encountered while reading the stream, or nil if
// the stream finished cleanly.
func (s *Stream) Err() error {
	return s.err
}

// Close releases the underlying HTTP connection. It is safe to call more than once.
func (s *Stream) Close() error {
	if s.resp != nil && s.resp.Body != nil {
		return s.resp.Body.Close()
	}
	return nil
}

// sseKind classifies a single SSE line.
type sseKind int

const (
	sseSkip sseKind = iota // a comment, blank, non-data or malformed line to ignore
	sseData                // a data line carrying a JSON chunk
	sseDone                // the terminal [DONE] sentinel
)

// parseSSELine parses one Server-Sent-Events line into a chunk and its kind. Only
// "data:" lines carry payloads; everything else is skipped, as are empty or non-JSON
// payloads so a single bad chunk never kills the stream.
func parseSSELine(line string) (json.RawMessage, sseKind) {
	line = strings.TrimRight(line, "\r\n")
	if !strings.HasPrefix(line, "data:") {
		return nil, sseSkip
	}
	payload := strings.TrimSpace(line[len("data:"):])
	if payload == "" {
		return nil, sseSkip
	}
	if payload == "[DONE]" {
		return nil, sseDone
	}
	if !json.Valid([]byte(payload)) {
		return nil, sseSkip
	}
	return json.RawMessage(payload), sseData
}
