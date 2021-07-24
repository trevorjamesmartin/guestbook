(ns guestbook.validation
  [:require
   [struct.core :as st]])

(def message-schema
  [[:message
    st/required
    st/string
    {:message "message must contain a minimum of 2 chars"
     :validate (fn [msg] (>= (count msg) 2))}]])

(defn validate-message [params]
    (first (st/validate params message-schema)))
