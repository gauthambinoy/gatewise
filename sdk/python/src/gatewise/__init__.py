"""GateWise — Python client for the GateWise AI governance gateway.

The gateway is OpenAI-compatible, so existing OpenAI code works by pointing it at your
gateway. This package adds typed errors and first-class access to GateWise's governance
endpoints (moderations, usage, audit, models, policies).

Quickstart::

    from gatewise import GateWiseClient

    client = GateWiseClient(base_url="http://localhost:8080", api_key="gatewise_sk_...")
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
    GateWiseError,
    BadRequestError,
    NotFoundError,
    PolicyDeniedError,
    RateLimitError,
    UpstreamError,
)
from .client import GateWiseClient

__version__ = "0.1.0"

__all__ = [
    "GateWiseClient",
    "GateWiseError",
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
