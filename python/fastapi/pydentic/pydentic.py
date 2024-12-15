from fastapi import FastAPI, HTTPException
from typing import Optional, List, Dict
from pydantic import BaseModel

app = FastAPI()


class Post(BaseModel):
    id: int
    title: str
    body: str


posts = [
    {"id": 1, "title": "News 1", "body": "Text 1"},
    {"id": 2, "title": "News 2", "body": "Text 2"},
    {"id": 3, "title": "News 3", "body": "Text 3"},
]


# @app.get("/items")
# async def items() -> List[Post]:
#     post_object = []
#     for post in posts:
#         post_object.append(Post(id=post["id"], title=post["title"], body=post["body"]))
#     return posts_object


@app.get("/items")
async def items() -> List[Post]:
    return [Post(**post) for post in posts]


@app.get("/items/{id}")
async def items(id: int) -> Post:
    for post in posts:
        if post["id"] == id:
            return Post(**post)

    raise HTTPException(status_code=404, detail="post not found")


@app.get("/search/")
async def search(post_id: Optional[int] = None) -> Dict[str, Optional[Post]]:
    if post_id:
        for post in posts:
            if post["id"] == post_id:
                return {"data": Post(**post)}
        raise HTTPException(status_code=404, detail="post not found")

    else:
        return {"data": None}
