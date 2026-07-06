"""Synchronous client for the GateWise AI governance gateway.

GateWise is an OpenAI-compatible HTTP gateway. This client speaks its ``/v1`` API directly,
adds typed errors, and exposes the gateway's native governance endpoints (moderations,
usage, audit, models, policies) alongside the familiar chat / embeddings / images calls.

If you are already using the OpenAI Python SDK you do not need this package at all — just
point that client's ``base_url`` and ``api_key`` at your gateway. This client exists for
callers that want the governance helpers and typed errors without pulling in the OpenAI
SDK.
"""

from __future__ import annotations

import json
import os
from typing import Any, Dict, Iterator, List, Mapping, Optional, Sequence, Union

import httpx

from ._errors import (
    APIConnectionError,
    APITimeoutError,
    GateWiseError,
    error_from_response,
)

__all__ = ["GateWiseClient"]

DEFAULT_BASE_URL = "http://localhost:8080"
DEFAULT_TIMEOUT = 60.0

# A JSON-ish value. The gateway returns provider responses verbatim, so we keep these loose.
JSON = Any
Messages = Sequence[Mapping[str, Any]]


class GateWiseClient:
    """A thin, typed client over the GateWise gateway's ``/v1`` HTTP API.

    The base URL is the gateway host (the ``/v1`` prefix is added for you), e.g.
    ``http://localhost:8080`` locally or ``https://gatewise.<host>.nip.io`` for a hosted
    deployment.

    Args:
        base_url: Gateway host. Falls back to ``GATEWISE_BASE_URL``, then ``http://localhost:8080``.
        api_key: GateWise API key (``gatewise_sk_...``). Falls back to ``GATEWISE_API_KEY``.
        timeout: Per-request timeout in seconds.
        http_client: An existing ``httpx.Client`` to reuse (advanced). When supplied, its
            configuration is used as-is and is not closed by this client.

    The client is usable as a context manager so the underlying connection pool is closed
    promptly::

        with GateWiseClient(api_key="gatewise_sk_...") as client:
            client.chat.completions.create(model="smart", messages=[...])
    """

    def __init__(
        self,
        *,
        base_url: Optional[str] = None,
        api_key: Optional[str] = None,
        timeout: Optional[float] = DEFAULT_TIMEOUT,
        http_client: Optional[httpx.Client] = None,
    ) -> None:
        resolved_base = (base_url or os.environ.get("GATEWISE_BASE_URL") or DEFAULT_BASE_URL).rstrip("/")
        resolved_key = api_key if api_key is not None else os.environ.get("GATEWISE_API_KEY")
        if not resolved_key:
            raise ValueError(
                "An GateWise API key is required. Pass api_key=... or set the GATEWISE_API_KEY "
                "environment variable."
            )

        self.base_url = resolved_base
        self.api_key = resolved_key
        self.timeout = timeout

        # We own the client only when we created it, so we only close what we made.
        self._owns_client = http_client is None
        if http_client is None:
            http_client = httpx.Client(timeout=timeout)
        self._http = http_client

        headers = {
            "Authorization": f"Bearer {resolved_key}",
            "Content-Type": "application/json",
            "Accept": "application/json",
            "User-Agent": "gatewise-python/0.1.0",
        }
        # Merge onto any headers the caller pre-configured on a supplied client.
        self._http.headers.update(headers)

        # Namespaced resources mirroring the OpenAI client shape.
        self.chat = Chat(self)
        self.embeddings = Embeddings(self)
        self.images = Images(self)
        self.moderations = Moderations(self)

    # -- lifecycle ---------------------------------------------------------------------

    def close(self) -> None:
        """Close the underlying HTTP connection pool, if this client owns it."""
        if self._owns_client:
            self._http.close()

    def __enter__(self) -> "GateWiseClient":
        return self

    def __exit__(self, *exc: object) -> None:
        self.close()

    # -- low-level request plumbing ----------------------------------------------------

    def _url(self, path: str) -> str:
        """Join the gateway base URL with a ``/v1`` API path."""
        return f"{self.base_url}/v1/{path.lstrip('/')}"

    def _request(
        self,
        method: str,
        path: str,
        *,
        json_body: Optional[Mapping[str, Any]] = None,
        params: Optional[Mapping[str, Any]] = None,
    ) -> JSON:
        """Send a request and return the decoded JSON body, raising typed errors on failure."""
        try:
            response = self._http.request(
                method,
                self._url(path),
                json=json_body,
                params=_clean_params(params),
            )
        except httpx.TimeoutException as exc:
            raise APITimeoutError(f"Request to GateWise timed out: {exc}") from exc
        except httpx.HTTPError as exc:
            raise APIConnectionError(f"Could not reach the GateWise gateway: {exc}") from exc

        return self._handle(response)

    @staticmethod
    def _handle(response: httpx.Response) -> JSON:
        """Decode a response, raising a typed ``GateWiseError`` for any non-2xx status."""
        body: Any
        try:
            body = response.json() if response.content else None
        except ValueError:
            # Non-JSON body (e.g. an upstream 5xx in plain text). Keep the raw text.
            body = response.text or None

        if response.is_success:
            return body
        raise error_from_response(response.status_code, body)

    def _stream(
        self,
        path: str,
        *,
        json_body: Mapping[str, Any],
    ) -> Iterator[Dict[str, Any]]:
        """POST a streaming request and yield each parsed SSE ``data:`` chunk as a dict.

        Server-Sent Events arrive as ``data: {json}`` lines terminated by a blank line, with
        a final ``data: [DONE]`` sentinel that is consumed (not yielded). Non-JSON or empty
        ``data:`` payloads are skipped defensively.
        """
        try:
            with self._http.stream(
                "POST", self._url(path), json=json_body
            ) as response:
                if not response.is_success:
                    # Pull the (small) error body so we can raise a typed error.
                    response.read()
                    body: Any
                    try:
                        body = json.loads(response.text) if response.text else None
                    except ValueError:
                        body = response.text or None
                    raise error_from_response(response.status_code, body)

                for line in response.iter_lines():
                    chunk = _parse_sse_line(line)
                    if chunk is _SSE_DONE:
                        return
                    if chunk is not None:
                        yield chunk
        except httpx.TimeoutException as exc:
            raise APITimeoutError(f"Streaming request to GateWise timed out: {exc}") from exc
        except httpx.HTTPError as exc:
            raise APIConnectionError(f"Could not reach the GateWise gateway: {exc}") from exc

    # -- governance + metadata endpoints -----------------------------------------------

    def usage(self) -> JSON:
        """Return the calling tenant's usage and cost summary (``GET /v1/usage``)."""
        return self._request("GET", "usage")

    def audit(
        self,
        *,
        q: Optional[str] = None,
        verdict: Optional[str] = None,
    ) -> JSON:
        """Query the tenant's audit log (``GET /v1/audit``).

        Args:
            q: Free-text filter over the redacted prompt, model and actor.
            verdict: Filter to one verdict, e.g. ``ALLOWED``, ``REDACTED`` or ``BLOCKED``.

        Returns:
            A page of audit entries with paging metadata
            (``{"entries": [...], "page", "size", "total"}``).
        """
        return self._request("GET", "audit", params={"q": q, "verdict": verdict})

    def models(self) -> JSON:
        """List the routing table / allowed model aliases (``GET /v1/models``)."""
        return self._request("GET", "models")

    def policies(self) -> JSON:
        """List the tenant's policy rules (``GET /v1/policies``)."""
        return self._request("GET", "policies")


class Chat:
    """The ``chat`` namespace, holding ``completions``."""

    def __init__(self, client: GateWiseClient) -> None:
        self.completions = ChatCompletions(client)


class ChatCompletions:
    """``client.chat.completions`` — the OpenAI-compatible chat endpoint."""

    def __init__(self, client: GateWiseClient) -> None:
        self._client = client

    def create(
        self,
        *,
        model: str,
        messages: Messages,
        stream: bool = False,
        **kwargs: Any,
    ) -> Union[JSON, Iterator[Dict[str, Any]]]:
        """Create a chat completion (``POST /v1/chat/completions``).

        Args:
            model: A model alias the gateway routes (e.g. ``smart``, ``fast``).
            messages: OpenAI-style message list, e.g.
                ``[{"role": "user", "content": "Hello"}]``.
            stream: When ``True``, return an iterator of parsed SSE chunk dicts instead of a
                single response. The iterator stops at the gateway's ``[DONE]`` sentinel.
            **kwargs: Any other OpenAI chat parameter (``temperature``, ``max_tokens``,
                ``user``, ``tools``, ...). Passed through verbatim.

        Returns:
            The parsed completion dict, or — when ``stream=True`` — an iterator of chunk dicts.
        """
        body: Dict[str, Any] = {"model": model, "messages": list(messages), **kwargs}
        if stream:
            body["stream"] = True
            return self._client._stream("chat/completions", json_body=body)
        body["stream"] = False
        return self._client._request("POST", "chat/completions", json_body=body)


class Embeddings:
    """``client.embeddings`` — the OpenAI-compatible embeddings endpoint."""

    def __init__(self, client: GateWiseClient) -> None:
        self._client = client

    def create(
        self,
        *,
        model: str,
        input: Union[str, Sequence[str]],
        **kwargs: Any,
    ) -> JSON:
        """Create embeddings (``POST /v1/embeddings``).

        Args:
            model: The embeddings model alias.
            input: A single string or a list of strings to embed.
            **kwargs: Any other OpenAI embeddings parameter.

        Returns:
            The parsed embeddings response dict.
        """
        body: Dict[str, Any] = {"model": model, "input": input, **kwargs}
        return self._client._request("POST", "embeddings", json_body=body)


class Images:
    """``client.images`` — the OpenAI-compatible image-generation endpoint."""

    def __init__(self, client: GateWiseClient) -> None:
        self._client = client

    def generate(
        self,
        *,
        model: str,
        prompt: str,
        **kwargs: Any,
    ) -> JSON:
        """Generate images (``POST /v1/images/generations``).

        Args:
            model: The image model alias.
            prompt: The (governed, redacted-before-egress) text prompt.
            **kwargs: Any other OpenAI image parameter (``size``, ``n``, ...).

        Returns:
            The parsed images response dict.
        """
        body: Dict[str, Any] = {"model": model, "prompt": prompt, **kwargs}
        return self._client._request("POST", "images/generations", json_body=body)


class Moderations:
    """``client.moderations`` — GateWise's native, provider-free content screen."""

    def __init__(self, client: GateWiseClient) -> None:
        self._client = client

    def create(self, *, input: str) -> JSON:
        """Screen text locally for sensitive data and prompt injection (``POST /v1/moderations``).

        This never calls a model provider — nothing leaves the gateway. Use it to pre-screen
        content before sending it anywhere.

        Args:
            input: The text to screen.

        Returns:
            ``{"flagged": bool, "sensitiveData": {type: count}, "injection": [categories]}``.
        """
        return self._client._request("POST", "moderations", json_body={"input": input})


# -- module-level helpers --------------------------------------------------------------

# Sentinel signalling the SSE stream's terminal ``[DONE]`` marker.
_SSE_DONE = object()


def _parse_sse_line(line: str) -> Union[None, Dict[str, Any], object]:
    """Parse one SSE line into a chunk dict, the ``_SSE_DONE`` sentinel, or ``None`` to skip.

    Only ``data:`` lines carry payloads; comments, blank lines and other SSE fields
    (``event:``, ``id:``) are ignored.
    """
    if not line or not line.startswith("data:"):
        return None
    payload = line[len("data:"):].strip()
    if not payload:
        return None
    if payload == "[DONE]":
        return _SSE_DONE
    try:
        return json.loads(payload)
    except ValueError:
        # A malformed chunk shouldn't kill the whole stream; skip it.
        return None


def _clean_params(params: Optional[Mapping[str, Any]]) -> Optional[Dict[str, Any]]:
    """Drop ``None``-valued query params so they aren't serialized as empty strings."""
    if not params:
        return None
    return {key: value for key, value in params.items() if value is not None}
