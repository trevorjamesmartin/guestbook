ALTER TABLE posts
  ADD COLUMN parent integer REFERENCES posts(id);
