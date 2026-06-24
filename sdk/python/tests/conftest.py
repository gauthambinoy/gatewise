"""Shared pytest fixtures for the Auvex client tests.

These tests never touch the network: every request is intercepted by ``respx``. A fresh
client is created against a fixed base URL and key so assertions can check the exact URL,
verb and headers that the client produced.
"""

import pytest

from auvex import AuvexClient

BASE_URL = "http://gateway.test"
API_KEY = "auvex_sk_test_123"


@pytest.fixture()
def client():
    """An AuvexClient pointed at the fake gateway, closed after each test."""
    with AuvexClient(base_url=BASE_URL, api_key=API_KEY, timeout=5.0) as instance:
        yield instance
