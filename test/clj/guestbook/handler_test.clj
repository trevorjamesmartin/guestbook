(ns guestbook.handler-test
  (:require
   [clojure.test :refer :all]
   [ring.mock.request :refer :all]
   [guestbook.handler :refer :all]
   [guestbook.middleware.formats :as formats]
   [muuntaja.core :as m]
   [buddy.hashers :as hashers]
   [clojure.instant :refer [read-instant-date]]
   [mount.core :as mount]))

(defn parse-json [body]
  (m/decode formats/instance "application/json" body))

(use-fixtures
  :once
  (fn [f]
    (mount/start #'guestbook.config/env
                 #'guestbook.routes.websockets/socket
                 #'guestbook.handler/routes)
    (f)))

(defn login-request [login pass]
  (-> (request :post "/api/login")
      (json-body {:login    login
                  :password pass})))

(def now (java.util.Date. 0))

(defn mock-user [{:keys [login]}]
  (when (= login "foo")
    {:login "foo"
     :password (hashers/encrypt "password")
     :created_at now
     :profile {}}))

(deftest test-app
  (testing "main route"
    (let [response ((app) (request :get "/"))]
      (is (= 200 (:status response)))))

  (testing "not-found route"
    (let [response ((app) (request :get "/invalid"))]
      (is (= 404 (:status response))))))

(deftest test-login
  (with-redefs [guestbook.db.core/get-user-for-auth* mock-user]
    (let [handler (app)]
      (testing "login success"
        (let [{:keys [body status] :as r} (handler
                                           (login-request "foo" "password"))
              json (parse-json body)]
          (println (assoc r :body json))
          (is (= 200 status))
          (is (= (->
                  (:identity json)
                  (update :created_at read-instant-date))
                 (-> {:login "foo"}
                     mock-user
                     (dissoc :password))))))
      (testing "password mismatch"
        (let [{:keys [status]} (handler (login-request "foo" "hacker"))]
          (is (= 401 status)))))))
