CREATE OR REPLACE VIEW reply_count as
SELECT p.id AS id, count(c.id) AS reply_count
 FROM posts p LEFT JOIN posts c ON c.parent = p.id
 GROUP by p.id
 --;;
CREATE OR REPLACE VIEW posts_with_replies AS
SELECT * FROM
(WITH RECURSIVE posts_with_replies AS
 (WITH replies AS
  (SELECT p.parent AS parent,
          p.id AS id,
          to_jsonb(pwm) as msg,
          p.id AS post_id
          FROM posts p
          LEFT JOIN posts_with_meta pwm
          ON p.id = pwm.id)
        SELECT parent, id, msg, post_id
        FROM replies
        UNION
        SELECT r.parent, r.id, r.msg, p.post_id
        FROM replies r
            INNER JOIN posts_with_replies p
            ON r.id = p.parent)
        SELECT  post_id                     AS id,
                jsonb_agg(msg)              AS messages,
                (array_agg(id))[count(id)]  AS root_id,
                count(id) <> 1              AS is_reply
        FROM posts_with_replies
        GROUP BY post_id) as pwr
--;;
ALTER VIEW posts_and_boosts RENAME TO posts_and_boosts_no_replies
--;;
CREATE OR REPLACE VIEW posts_and_boosts AS
select
*
FROM posts_with_replies
INNER JOIN reply_count using (id)
INNER JOIN posts_and_boosts_no_replies using (id)
