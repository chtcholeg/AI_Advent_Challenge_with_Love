"""
Абстракция над LLM-провайдерами.

Провайдеры:
  gigachat — официальный SDK Sber (GigaChat / Pro / Max)
  ollama   — OpenAI-совместимый API (Ollama, llama.cpp и др.)
"""

from __future__ import annotations

from dataclasses import dataclass, field


@dataclass
class LLMConfig:
    provider: str                          # "gigachat" | "ollama"
    gigachat_credentials: str = ""
    gigachat_model: str = "GigaChat"
    ollama_base_url: str = "http://localhost:11434"
    ollama_model: str = "qwen2.5:7b"


def chat(config: LLMConfig, system: str, user: str) -> str:
    """Одиночный запрос без истории диалога."""
    if config.provider == "ollama":
        return _ollama_chat(config, [
            {"role": "system", "content": system},
            {"role": "user", "content": user},
        ])
    return _gigachat_chat_once(config, system, user)


def _gigachat_chat_once(config: LLMConfig, system: str, user: str) -> str:
    from gigachat import GigaChat
    from gigachat.models import Chat, Messages, MessagesRole

    giga = GigaChat(
        credentials=config.gigachat_credentials,
        model=config.gigachat_model,
        verify_ssl_certs=False,
    )
    response = giga.chat(Chat(messages=[
        Messages(role=MessagesRole.SYSTEM, content=system),
        Messages(role=MessagesRole.USER, content=user),
    ]))
    return response.choices[0].message.content


def _ollama_chat(config: LLMConfig, messages: list[dict]) -> str:
    from openai import OpenAI

    client = OpenAI(
        base_url=f"{config.ollama_base_url.rstrip('/')}/v1",
        api_key="ollama",
    )
    response = client.chat.completions.create(
        model=config.ollama_model,
        messages=messages,
    )
    return response.choices[0].message.content


class ChatSession:
    """Многотурный диалог по документу (для Q&A)."""

    def __init__(self, config: LLMConfig, document: str = "", system_prompt: str | None = None):
        self._config = config
        if system_prompt is not None:
            self._history: list[dict] = [{"role": "system", "content": system_prompt}]
        else:
            from prompts import SYSTEM_PROMPT
            self._history = [{"role": "system", "content": SYSTEM_PROMPT.format(document=document)}]
        self._giga = None  # GigaChat instance, ленивая инициализация

    def ask(self, question: str) -> str:
        self._history.append({"role": "user", "content": question})
        answer = self._send()
        self._history.append({"role": "assistant", "content": answer})
        return answer

    def _send(self) -> str:
        if self._config.provider == "ollama":
            return _ollama_chat(self._config, self._history)
        return self._gigachat_send()

    def _gigachat_send(self) -> str:
        from gigachat import GigaChat
        from gigachat.models import Chat, Messages, MessagesRole

        if self._giga is None:
            self._giga = GigaChat(
                credentials=self._config.gigachat_credentials,
                model=self._config.gigachat_model,
                verify_ssl_certs=False,
            )

        _role_map = {
            "system": MessagesRole.SYSTEM,
            "user": MessagesRole.USER,
            "assistant": MessagesRole.ASSISTANT,
        }
        msgs = [
            Messages(role=_role_map[m["role"]], content=m["content"])
            for m in self._history
        ]
        response = self._giga.chat(Chat(messages=msgs))
        return response.choices[0].message.content
