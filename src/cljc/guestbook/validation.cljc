(ns guestbook.validation
  [:require
   [struct.core :as st]])

(def message-schema
  [[:name
    st/required
    st/string]
   [:message
    st/required
    st/string]])

(defn validate-message [params]
    (first (st/validate params message-schema)))
