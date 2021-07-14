-- :name save-message! :! :n
-- :doc create a new message using the name and message keys
INSERT INTO guestbook
(name, message)
VALUES (:name, :message)

-- :name get-messages :? :*
-- :doc selects all available messages
SELECT * FROM guestbook
