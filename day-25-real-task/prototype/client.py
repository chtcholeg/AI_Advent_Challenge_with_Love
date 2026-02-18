from gigachat import GigaChat
from gigachat.models import Chat, Messages, MessagesRole

from prompts import SYSTEM_PROMPT


class DocumentAnalyzer:
    """Клиент для Q&A-диалога по документу через GigaChat.

    Хранит историю диалога, что позволяет задавать уточняющие
    вопросы по уже проанализированному документу.
    """

    def __init__(self, credentials: str, model: str, document: str):
        self._giga = GigaChat(
            credentials=credentials,
            model=model,
            verify_ssl_certs=False,
        )
        self._history: list[Messages] = [
            Messages(
                role=MessagesRole.SYSTEM,
                content=SYSTEM_PROMPT.format(document=document),
            )
        ]

    def ask(self, question: str) -> str:
        """Ответ на вопрос пользователя по документу."""
        return self._send(question)

    def _send(self, user_message: str) -> str:
        self._history.append(
            Messages(role=MessagesRole.USER, content=user_message)
        )
        response = self._giga.chat(Chat(messages=self._history))
        answer = response.choices[0].message.content
        self._history.append(
            Messages(role=MessagesRole.ASSISTANT, content=answer)
        )
        return answer
