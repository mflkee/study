"""Прототип RAG по базе знаний вымышленной компании «МетроСервис».

Лабораторная «Промпт-инжиниринг и основы RAG» (Методы ИИ I, ТюмГУ).
Этап 1 — ingestion: загрузка текста, чанки 500/50, эмбеддинги, Chroma.
Этап 2 — цепочка: retriever top-3 → строгий промпт → LLM.

Запуск:
    source ~/.config/zsh/secrets.zsh   # ROUTERAI_API_KEY
    .venv/bin/python rag_demo.py
"""

from __future__ import annotations

import os
import sys
import time

from langchain_chroma import Chroma
from langchain_community.document_loaders import TextLoader
from langchain_core.output_parsers import StrOutputParser
from langchain_core.prompts import ChatPromptTemplate
from langchain_huggingface import HuggingFaceEmbeddings
from langchain_openai import ChatOpenAI
from langchain_text_splitters import RecursiveCharacterTextSplitter

KB_PATH = "knowledge_base.md"
CHROMA_DIR = "chroma"
EMBED_MODEL = "intfloat/multilingual-e5-small"
LLM_MODEL = os.environ.get("RAG_LLM_MODEL", "deepseek/deepseek-v4.1-flash")

QUESTIONS = [
    # три вопроса, ответы на которые есть в базе знаний
    "Сколько времени занимает поверка на стенде и поверка на месте эксплуатации?",
    "Какие документы получает заказчик и куда вносится результат поверки?",
    "При каком объёме работ дают скидку и сколько процентов?",
    # вопрос, ответа на который в базе знаний нет
    "Какой ИНН у компании «МетроСервис»?",
]

PROMPT = ChatPromptTemplate.from_messages(
    [
        (
            "system",
            "Ты — ассистент службы метрологии. Отвечай ТОЛЬКО на основании "
            "фрагментов контекста, которые приведены ниже.\n"
            "Правила:\n"
            "1. Не используй знания вне контекста, не додумывай факты и числа.\n"
            "2. Если в контексте нет ответа, ответь ровно так: "
            "«В базе знаний нет информации об этом.»\n"
            "3. Отвечай кратко, 1–3 предложениями, можно цитировать числа из контекста.\n"
            "4. В конце ответа укажи, на какие фрагменты ты опирался: [Ф1], [Ф2].",
        ),
        (
            "human",
            "Контекст:\n{context}\n\nВопрос: {question}",
        ),
    ]
)


class E5Embeddings(HuggingFaceEmbeddings):
    """E5 требует префиксы "query: " / "passage: " — добавляем их перед кодированием."""

    def embed_documents(self, texts: list[str]) -> list[list[float]]:
        return super().embed_documents([f"passage: {t}" for t in texts])

    def embed_query(self, text: str) -> list[float]:
        return super().embed_query(f"query: {text}")


def build_store() -> Chroma:
    """Этап 1: загрузка → чанки → эмбеддинги → Chroma."""
    docs = TextLoader(KB_PATH, encoding="utf-8").load()
    splitter = RecursiveCharacterTextSplitter.from_tiktoken_encoder(
        chunk_size=500,
        chunk_overlap=50,
        separators=["\n\n", "\n", ". ", " "],
    )
    chunks = splitter.split_documents(docs)
    print(f"[ingestion] документов: {len(docs)}, чанков: {len(chunks)}")
    for i, ch in enumerate(chunks, start=1):
        extra = f" ({ch.metadata['source']}, {len(ch.page_content)} симв.)"
        print(f"  чанк {i}{extra}: {ch.page_content[:70].replace(chr(10), ' ')}…")

    embeddings = E5Embeddings(model_name=EMBED_MODEL)
    store = Chroma.from_documents(
        documents=chunks,
        embedding=embeddings,
        collection_name="metroservice",
        collection_metadata={"hnsw:space": "cosine"},
        persist_directory=CHROMA_DIR,
    )
    print(f"[ingestion] векторное хранилище: {CHROMA_DIR}/ (модель {EMBED_MODEL})\n")
    return store


def main() -> int:
    if not os.environ.get("ROUTERAI_API_KEY"):
        print("Нет ROUTERAI_API_KEY: source ~/.config/zsh/secrets.zsh", file=sys.stderr)
        return 2

    store = build_store()
    llm = ChatOpenAI(
        model=LLM_MODEL,
        base_url="https://routerai.ru/api/v1",
        api_key=os.environ["ROUTERAI_API_KEY"],
        temperature=0,
        timeout=60,
    )

    for n, question in enumerate(QUESTIONS, start=1):
        print("=" * 78)
        print(f"Вопрос {n}: {question}")
        t0 = time.time()
        hits = store.similarity_search_with_relevance_scores(question, k=3)
        context = "\n\n".join(
            f"[Ф{i}] (релевантность {score:.3f})\n{doc.page_content}"
            for i, (doc, score) in enumerate(hits, start=1)
        )
        print("\nНайденные чанки (top-3, релевантность):")
        for i, (doc, score) in enumerate(hits, start=1):
            snippet = doc.page_content.replace("\n", " ")[:110]
            print(f"  [Ф{i}] {score:.3f} — {snippet}…")

        answer = (PROMPT | llm | StrOutputParser()).invoke(
            {"context": context, "question": question}
        )
        print(f"\nОтвет модели ({time.time() - t0:.1f} с):\n{answer}\n")

    print("=" * 78)
    print("Готово: 3 вопроса с ответом в базе, 1 — контрольный (ответа нет).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
