"""Auvex — Python client for the Auvex AI governance gateway.

The gateway is OpenAI-compatible, so existing OpenAI code works by pointing it at your
gateway. This package adds typed errors and first-class access to Auvex's governance
endpoints (moderations, usage, audit, models, policies).

Quickstart::

    from auvex import AuvexClient

    client = AuvexClient(base_url="http://localhost:8080", api_key="auvex_sk_...")
    reply = client.chat.completions.create(
        model="smart",
        messages=[{"role": "user", "content": "Hello"}],
    )
    print(reply["choices"][0]["message"]["content"])
"""

from ._errors import (
    APIConnectionError,
    APITimeoutError,
    AuthenticationError,
    AuvexError,
    BadRequestError,
    NotFoundError,
    PolicyDeniedError,
    RateLimitError,
    UpstreamError,
)
from .client import AuvexClient

__version__ = "0.1.0"

__all__ = [
    "AuvexClient",
    "AuvexError",
    "APIConnectionError",
    "APITimeoutError",
    "AuthenticationError",
    "BadRequestError",
    "NotFoundError",
    "PolicyDeniedError",
    "RateLimitError",
    "UpstreamError",
    "__version__",
]
