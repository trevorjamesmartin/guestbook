-- :name save-message! :! :n
-- :doc create a new message using the name and message keys
INSERT INTO posts
(author, name, message, parent)
VALUES (:author, :name, :message, :parent)
RETURNING *;

-- :name get-messages :? :*
-- :doc selects all available messages
SELECT * FROM posts_with_meta

-- :name get-message :? :1
-- :doc selects a message
SELECT * FROM posts_with_meta
              INNER JOIN (SELECT id, parent from posts) as p using (id)
              INNER JOIN reply_count using (id)
  WHERE id = :id

-- :name get-replies :? :*
-- :doc get the replies for a post
SELECT * FROM posts_with_meta
              INNER JOIN (SELECT id, parent from posts) as p using (id)
              INNER JOIN reply_count using (id)
  WHERE id IN (select id from posts
                where parent = :id)
                
-- :name get-messages-by-author :? :*
-- :doc selects all messages posted by a user
SELECT * FROM posts_with_meta
WHERE author = :author

-- :name create-user!* :! :n
-- :doc creates a new user with the provided login and hashed password
INSERT INTO users
(login, password)
VALUES (:login, :password)

-- :name get-user-for-auth* :? :1
-- :doc selects a user for authentication
SELECT * FROM users
WHERE login = :login

-- :name get-messages-by-author :? :*
-- :doc selects all messages posted by a user
SELECT
  p.id              as id,
  p.timestamp       as timestamp,
  p.message         as message,
  p.name            as name,
  p.author          as author,
  a.profile->>'avatar' as avatar
FROM posts AS p JOIN users AS a
ON a.login = p.author
WHERE author = :author

-- :name set-profile-for-user* :<! :1
-- :doc sets a profile map for the specified user
UPDATE users
SET profile = :profile
where :login = login
RETURNING *;

-- :name get-user* :? :1
-- :doc gets a user's publicly available information
SELECT login, created_at, profile from users
WHERE login = :login

-- :name save-file! :! :n
-- :doc saves a file to the database
INSERT INTO media
(name, type, owner, data)
VALUES (:name, :type, :owner, :data)
ON CONFLICT (name) DO UPDATE
SET type = :type,
    data = :data
WHERE media.owner = :owner

-- :name get-file :? :1
-- :doc gets a file from the database
SELECT * FROM media
WHERE name = :name

-- :name set-password-for-user!* :! :n
UPDATE users
SET password = :password
WHERE login = :login

-- :name delete-user!* :! :n
DELETE FROM users
WHERE login = :login

-- :name boost-post! :! :n
-- :doc Boosts a post, or moves a boost to the top of the user's timeline
INSERT INTO boosts
(user_id, post_id, poster)
VALUES (:user, :post, nullif(:poster, :user))
ON CONFLICT (user_id, post_id) DO UPDATE
SET timestamp = now()
WHERE boosts.user_id = :user
AND   boosts.post_id = :post

-- :name boosters-of-post :? :*
-- :doc Get all boosters of a post
SELECT user_id as user FROM boosts
WHERE post_id = :post

-- :name get-reboosts :? :*
-- :doc Get all boosts descended from a given boost
WITH RECURSIVE reboosts AS
(WITH post_boosts AS
  (SELECT user_id, poster
    FROM boosts
    WHERE post_id = :post)    
    SELECT user_id, poster
    FROM post_boosts
    WHERE user_id = :user
    UNION
    SELECT b.user_id, b.poster
    FROM post_boosts b INNER JOIN reboosts r ON r.user_id = b.poster)
SELECT user_id as user, poster as source from reboosts

-- :name get-boost-chain :? :*
-- :doc Gets all boosts above the original boost
WITH RECURSIVE reboosts AS
(WITH post_boosts AS
  (SELECT user_id, poster
  FROM boosts
  WHERE post_id = :post)
  SELECT user_id, poster
  FROM post_boosts
  WHERE user_id = :user
  UNION
  SELECT b.user_id, b.poster
  FROM post_boosts b INNER JOIN reboosts r on r.poster = b.user_id)
SELECT user_id AS user, poster AS source FROM reboosts

-- :name get-timeline :? :*
-- :doc Gets the latest post or boost for each post
SELECT * FROM
(SELECT DISTINCT ON (p.id) * FROM posts_and_boosts as p
  ORDER BY p.id, p.posted_at desc) AS t
ORDER BY t.posted_at asc

-- :name get-timeline-for-poster :? :*
-- :doc Gets the latest post or boost for each post
SELECT * FROM
(SELECT DISTINCT ON (p.id) * FROM posts_and_boosts as p
  WHERE p.poster = :poster
  ORDER BY p.id, p.posted_at desc) AS t
ORDER BY t.posted_at asc

-- :name get-timeline-post :? :1
-- :doc Gets the boosted post for updating timelines
SELECT * FROM posts_and_boosts
WHERE is_boost = :is_boost
AND poster = :user
AND id = :post
ORDER BY posted_at asc
LIMIT 1

-- :name get-parents
-- :doc Gets the parents of a reply
SELECT * FROM posts_with_meta
              INNER JOIN (SELECT id, parent from posts) as p using (id)
              INNER JOIN reply_count using (id)
  WHERE id in (WITH RECURSIVE parents AS
                (SELECT id, parent from posts
                  WHERE id = :id
                UNION
                SELECT p.id, p.parent FROM posts p
                              INNER JOIN parents pp
                                      ON p.id = pp.parent)
              SELECT id from parents)

-- :name get-feed-for-tag :? :*
-- :require [guestbook.db.util :refer [tag-regex]]
-- :doc Given a tag, return its feed
SELECT * FROM
(SELECT DISTINCT ON (p.id) * FROM posts_and_boosts as p
  WHERE
  /*~ (if (:tag params) */
  p.message ~*
  /*~*/
  false
  /*~ ) ~*/
  --~ (when (:tag params) (tag-regex (:tag params)))
  ORDER BY p.id, posted_at desc) as t
ORDER BY t.posted_at asc
