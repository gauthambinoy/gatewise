"""Tests for the GateWise synchronous client. No network access — all mocked with respx."""

import httpx
import pytest
import respx

from gatewise import (
    AuthenticationError,
    GateWiseClient,
    BadRequestError,
    NotFoundError,
    PolicyDeniedError,
    RateLimitError,
    UpstreamError,
)
from gatewise._errors import error_from_response

from .conftest import API_KEY, BASE_URL

CHAT_REPLY = {"id": "cmpl-1", "choices": [{"message": {"content": "hi"}}]}


# -- configuration / env -----------------------------------------------------------------


def test_env_var_defaults(monkeypatch):
    """base_url and api_key fall back to GATEWISE_* environment variables."""
    monkeypatch.setenv("GATEWISE_BASE_URL", "http://env-host:9000")
    monkeypatch.setenv("GATEWISE_API_KEY", "gatewise_sk_env")
    client = GateWiseClient()
    assert client.base_url == "http://env-host:9000"
    assert client.api_key == "gatewise_sk_env"
    client.close()


def test_default_base_url(monkeypatch):
    """With nothing configured, the base URL defaults to localhost:8080."""
    monkeypatch.delenv("GATEWISE_BASE_URL", raising=False)
    client = GateWiseClient(api_key="gatewise_sk_x")
    assert client.base_url == "http://localhost:8080"
    client.close()


def test_missing_api_key_raises(monkeypatch):
    """A missing key is a clear ValueError, not a confusing 401 later."""
    monkeypatch.delenv("GATEWISE_API_KEY", raising=False)
    with pytest.raises(ValueError):
        GateWiseClient(base_url=BASE_URL)


def test_trailing_slash_stripped():
    """A trailing slash on base_url does not produce a double slash in the path."""
    client = GateWiseClient(base_url=BASE_URL + "/", api_key=API_KEY)
    assert client._url("usage") == f"{BASE_URL}/v1/usage"
    client.close()


# -- auth header + URL joining -----------------------------------------------------------


@respx.mock
def test_auth_header_is_sent(client):
    """Every request carries the Authorization: Bearer header."""
    route = respx.post(f"{BASE_URL}/v1/chat/completions").mock(
        return_value=httpx.Response(200, json=CHAT_REPLY)
    )
    client.chat.completions.create(model="smart", messages=[{"role": "user", "content": "hi"}])
    assert route.called
    sent = route.calls.last.request
    assert sent.headers["Authorization"] == f"Bearer {API_KEY}"
    assert sent.headers["Content-Type"] == "application/json"


@respx.mock
def test_base_url_joining(client):
    """The /v1 prefix is added exactly once onto the configured host."""
    route = respx.get(f"{BASE_URL}/v1/usage").mock(
        return_value=httpx.Response(200, json={"total": 1})
    )
    client.usage()
    assert route.called
    assert str(route.calls.last.request.url) == f"{BASE_URL}/v1/usage"


# -- each method hits the right path + verb ----------------------------------------------


@respx.mock
def test_chat_completions_path_and_body(client):
    route = respx.post(f"{BASE_URL}/v1/chat/completions").mock(
        return_value=httpx.Response(200, json=CHAT_REPLY)
    )
    out = client.chat.completions.create(
        model="smart",
        messages=[{"role": "user", "content": "hi"}],
        temperature=0.2,
    )
    assert out == CHAT_REPLY
    body = route.calls.last.request.read()
    assert b'"model":"smart"' in body.replace(b" ", b"")
    assert b'"temperature":0.2' in body.replace(b" ", b"")
    # Non-streaming requests explicitly send stream:false.
    assert b'"stream":false' in body.replace(b" ", b"")


@respx.mock
def test_embeddings(client):
    route = respx.post(f"{BASE_URL}/v1/embeddings").mock(
        return_value=httpx.Response(200, json={"data": [{"embedding": [0.1]}]})
    )
    out = client.embeddings.create(model="embed", input="hello")
    assert route.called
    assert out["data"][0]["embedding"] == [0.1]
    assert b'"input":"hello"' in route.calls.last.request.read().replace(b" ", b"")


@respx.mock
def test_embeddings_accepts_list_input(client):
    route = respx.post(f"{BASE_URL}/v1/embeddings").mock(
        return_value=httpx.Response(200, json={"data": []})
    )
    client.embeddings.create(model="embed", input=["a", "b"])
    assert b'"input":["a","b"]' in route.calls.last.request.read().replace(b" ", b"")


@respx.mock
def test_images_generate(client):
    route = respx.post(f"{BASE_URL}/v1/images/generations").mock(
        return_value=httpx.Response(200, json={"data": [{"url": "http://img"}]})
    )
    out = client.images.generate(model="image", prompt="a cat")
    assert route.called
    assert out["data"][0]["url"] == "http://img"
    assert b'"prompt":"acat"' in route.calls.last.request.read().replace(b" ", b"")


@respx.mock
def test_moderations(client):
    route = respx.post(f"{BASE_URL}/v1/moderations").mock(
        return_value=httpx.Response(
            200,
            json={"flagged": True, "sensitiveData": {"email": 1}, "injection": []},
        )
    )
    out = client.moderations.create(input="email jane@acme.com")
    assert route.called
    assert out["flagged"] is True
    assert out["sensitiveData"] == {"email": 1}
    assert b'"input":' in route.calls.last.request.read()


@respx.mock
def test_usage(client):
    respx.get(f"{BASE_URL}/v1/usage").mock(
        return_value=httpx.Response(200, json={"total": 5})
    )
    assert client.usage() == {"total": 5}


@respx.mock
def test_models(client):
    respx.get(f"{BASE_URL}/v1/models").mock(
        return_value=httpx.Response(200, json=[{"alias": "smart", "target": "gpt"}])
    )
    assert client.models()[0]["alias"] == "smart"


@respx.mock
def test_policies(client):
    respx.get(f"{BASE_URL}/v1/policies").mock(
        return_value=httpx.Response(200, json=[])
    )
    assert client.policies() == []


@respx.mock
def test_audit_query_params(client):
    route = respx.get(f"{BASE_URL}/v1/audit").mock(
        return_value=httpx.Response(200, json={"entries": [], "total": 0})
    )
    client.audit(q="card", verdict="BLOCKED")
    url = route.calls.last.request.url
    assert url.params["q"] == "card"
    assert url.params["verdict"] == "BLOCKED"


@respx.mock
def test_audit_omits_none_params(client):
    route = respx.get(f"{BASE_URL}/v1/audit").mock(
        return_value=httpx.Response(200, json={"entries": []})
    )
    client.audit()
    # No q/verdict were supplied, so neither should appear in the query string.
    url = route.calls.last.request.url
    assert "q" not in url.params
    assert "verdict" not in url.params


# -- error envelope -> typed exceptions --------------------------------------------------


@pytest.mark.parametrize(
    "status, error_type, expected",
    [
        (400, "invalid_request_error", BadRequestError),
        (401, "invalid_request_error", AuthenticationError),
        (403, "policy_violation", PolicyDeniedError),
        (403, "prompt_injection", PolicyDeniedError),
        (404, "not_found", NotFoundError),
        (429, "rate_limit_exceeded", RateLimitError),
        (502, "upstream_error", UpstreamError),
        (503, "upstream_error", UpstreamError),
        (504, "upstream_error", UpstreamError),
    ],
)
@respx.mock
def test_error_envelope_maps_to_exception(client, status, error_type, expected):
    """The OpenAI-style envelope is mapped to the right typed exception by status code."""
    respx.post(f"{BASE_URL}/v1/chat/completions").mock(
        return_value=httpx.Response(
            status,
            json={"error": {"message": "boom", "type": error_type, "code": "x"}},
        )
    )
    with pytest.raises(expected) as info:
        client.chat.completions.create(model="smart", messages=[{"role": "user", "content": "x"}])
    assert info.value.message == "boom"
    assert info.value.status_code == status
    assert info.value.type == error_type


def test_error_falls_back_to_type_for_unmapped_status():
    """When a status has no dedicated class, error.type selects the exception."""
    err = error_from_response(
        418, {"error": {"message": "teapot", "type": "rate_limit_exceeded"}}
    )
    assert isinstance(err, RateLimitError)
    assert err.message == "teapot"


def test_error_handles_non_dict_body():
    """A plain-text 5xx body still produces a usable error, not a crash."""
    err = error_from_response(500, "gateway exploded")
    assert err.status_code == 500
    assert "HTTP 500" in err.message


# -- streaming ---------------------------------------------------------------------------


def _sse_body(*chunks):
    """Render chunk dicts as an SSE stream ending in the [DONE] sentinel."""
    import json

    lines = [f"data: {json.dumps(chunk)}\n\n" for chunk in chunks]
    lines.append("data: [DONE]\n\n")
    return "".join(lines)


@respx.mock
def test_streaming_parses_sse(client):
    """stream=True yields each parsed chunk dict and stops at [DONE]."""
    body = _sse_body(
        {"choices": [{"delta": {"content": "Hel"}}]},
        {"choices": [{"delta": {"content": "lo"}}]},
    )
    route = respx.post(f"{BASE_URL}/v1/chat/completions").mock(
        return_value=httpx.Response(200, text=body, headers={"Content-Type": "text/event-stream"})
    )
    stream = client.chat.completions.create(
        model="smart", messages=[{"role": "user", "content": "hi"}], stream=True
    )
    chunks = list(stream)
    assert len(chunks) == 2
    assert chunks[0]["choices"][0]["delta"]["content"] == "Hel"
    assert chunks[1]["choices"][0]["delta"]["content"] == "lo"
    # The request advertised streaming to the gateway.
    assert b'"stream":true' in route.calls.last.request.read().replace(b" ", b"")


@respx.mock
def test_streaming_skips_blank_and_malformed_lines(client):
    """Blank lines and unparseable data: chunks are skipped, not fatal."""
    body = (
        "\n"
        "data: \n"
        "data: not-json\n"
        'data: {"choices": [{"delta": {"content": "ok"}}]}\n'
        "data: [DONE]\n"
    )
    respx.post(f"{BASE_URL}/v1/chat/completions").mock(
        return_value=httpx.Response(200, text=body)
    )
    chunks = list(
        client.chat.completions.create(
            model="smart", messages=[{"role": "user", "content": "hi"}], stream=True
        )
    )
    assert len(chunks) == 1
    assert chunks[0]["choices"][0]["delta"]["content"] == "ok"


@respx.mock
def test_streaming_error_status_raises(client):
    """A non-2xx response to a streaming request raises a typed error."""
    respx.post(f"{BASE_URL}/v1/chat/completions").mock(
        return_value=httpx.Response(
            403, json={"error": {"message": "denied", "type": "policy_violation"}}
        )
    )
    with pytest.raises(PolicyDeniedError):
        list(
            client.chat.completions.create(
                model="smart", messages=[{"role": "user", "content": "hi"}], stream=True
            )
        )
