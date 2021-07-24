(ns guestbook.routes.services
  (:require
   [spec-tools.data-spec :as ds]
   [reitit.swagger :as swagger]
   [reitit.swagger-ui :as swagger-ui]
   [reitit.ring.coercion :as coercion]
   [reitit.coercion.spec :as spec-coercion]
   [reitit.ring.middleware.muuntaja :as muuntaja]
   [reitit.ring.middleware.exception :as exception]
   [reitit.ring.middleware.multipart :as multipart]
   [reitit.ring.middleware.parameters :as parameters]
   [guestbook.messages :as msg]
   [guestbook.auth :as auth]
   [guestbook.auth.ring :refer [wrap-authorized get-roles-from-match]]
   [guestbook.author :as author]
   [clojure.tools.logging :as log]
   [ring.util.http-response :as response]
   [guestbook.middleware.formats :as formats]
   [clojure.java.io :as io]
   [guestbook.db.core :as db]
   [guestbook.media :as media]
   [clojure.spec.alpha :as s]
   [clojure.string :as string]))


(defn service-routes []
  ["/api"
   {:middleware [;; query-params & form-params
                 parameters/parameters-middleware
                 ;; content-negotiation
                 muuntaja/format-negotiate-middleware
                 ;; encoding response body
                 muuntaja/format-response-middleware
                 ;; exception handling
                 exception/exception-middleware
                 ;; decoding request body
                 muuntaja/format-request-middleware
                 ;; coercing response body
                 coercion/coerce-response-middleware
                 ;; coercing request params
                 coercion/coerce-request-middleware
                 ;; multipart forms
                 multipart/multipart-middleware
                 ;; custom
                 (fn [handler]
                   (wrap-authorized
                    handler
                    (fn handle-unauthorized [req]
                      (let [route-roles (get-roles-from-match req)]
                        (log/debug
                         "Roles for route: "
                         (:uri req)
                         route-roles)
                        (log/debug
                         "User is Unauthorized!"
                         (-> req
                             :session
                             :identity
                             :roles))
                        (response/forbidden
                         {:message
                          (str "User must have one of the following roles: "
                               route-roles)})))))]
    :muuntaja formats/instance
    :coercion spec-coercion/coercion
    :swagger {:id ::api}}
   ["" {:no-doc true
        ::auth/roles (auth/roles :swagger/swagger)}
    ["/swagger.json"
     {:get (swagger/create-swagger-handler)}]
    ["/swagger-ui*"
     {:get (swagger-ui/create-swagger-ui-handler
            {:url "/api/swagger.json"})}]]
   ["/session"
    {::auth/roles (auth/roles :session/get)
     :get
     {:responses
      {200
       {:body
        {:session
         {:identity
          (ds/maybe
           {:login string?
            :created_at inst?
            :profile map?})}}}}
      :handler
      (fn [{{:keys [identity]} :session}]
        (response/ok {:session
                      {:identity
                       (not-empty
                        (select-keys identity [:login :created_at :profile]))}}))}}]
   ["/author/:login"
    {::auth/roles (auth/roles :author/get)
     :get {:parameters
           {:path {:login string?}}

           :responses
           {200
            {:body map?}
            500
            {:errors map?}}
           :handler
           (fn [{{{:keys [login]} :path} :parameters}]
             (response/ok (author/get-author login)))}}]

   ["/my-account"
    ["/set-profile"
     {::auth/roles (auth/roles :account/set-profile!)
      :post {:parameters
             {:body
              {:profile map?}}

             :responses
             {200
              {:body map?}
              500
              {:errors map?}}

             :handler
             (fn [{{{:keys [profile]} :body} :parameters
                   {:keys [identity] :as session} :session}]
               (try
                 (let [identity
                       (author/set-author-profile (:login identity) profile)]
                   (update (response/ok {:success true})
                           :session
                           assoc :identity identity))
                 (catch Exception e
                   (log/error e)
                   (response/internal-server-error
                    {:errors {:server-error
                              "Failed to set profile!"}}))))}}]
    ["/media/upload"
     {::auth/roles (auth/roles :media/upload)
      :post
      {:parameters {:multipart (s/map-of keyword? multipart/temp-file-part)}
       :handler
       (fn [{{mp :multipart} :parameters
             {:keys [identity]} :session}]
         (response/ok
          (reduce-kv
           (fn [acc name {:keys [size content-type] :as file-part}]
             (cond
               (> size (* 5 1024 1024))
               (do
                 (log/error "File " name
                            " exceeded max size of 5 MB. (size: " size ")")
                 (update acc :failed-uploads (fnil conj []) name))
               (re-matches #"image/.*" content-type)
               (-> acc
                   (update :files-uploaded conj name)
                   (assoc name
                          (str "/api/media/"
                               (cond
                                 (= name :avatar)
                                 (media/insert-image-returning-name
                                  (assoc file-part
                                         :filename
                                         (str (:login identity) "_avatar.png"))
                                  {:width 128
                                   :height 128
                                   :owner (:login identity)})

                                 (= name :banner)
                                 (media/insert-image-returning-name
                                  (assoc file-part
                                         :filename
                                         (str (:login identity) "_banner.png"))
                                  {:width 1200
                                   :height 400
                                   :owner (:login identity)})

                                 :else
                                 (media/insert-image-returning-name
                                  (update
                                   file-part
                                   :filename
                                   string/replace #"|.[^|.]+$" ".png")
                                  {:max-width 800
                                   :max-height 2000
                                   :owner (:login identity)})))))

               :else
               (do
                 (log/error "Unsupported file type" content-type "for file" name)
                 (update acc :failed-uploads (fnil conj []) name))))
           {:files-uploaded []}
           mp)))}}]]

   ["/messages"
    {::auth/roles (auth/roles :messages/list)}
    ["" {:get
         {:responses
          {200
           {:body ;; Data Spec for response body
            {:messages
             [{:id pos-int?
               :name string?
               :message string?
               :timestamp inst?}]}}}
          :handler
          (fn [_]
            (response/ok (msg/message-list)))}}]
    ["/by/:author"
     {:get
      {:parameters {:path {:author string?}}
       :responses
       {200
        {:body
         {:messages
          [{:id pos-int?
            :name string?
            :message string?
            :timestamp inst?}]}}}
       :handler
       (fn [{{{:keys [author]} :path} :parameters}]
         (response/ok (msg/messages-by-author author)))}}]]

   ["/media/:name"
    {::auth/roles (auth/roles :media/get)
     :get {:parameters {:path {:name string?}}
           :handler (fn [{{{:keys [name]} :path} :parameters}]
                      (if-let [{:keys [data type]} (db/get-file {:name name})]
                        (-> (io/input-stream data)
                            (response/ok)
                            (response/content-type type))
                        (response/not-found)))}}]

   ["/login"
    {::auth/roles (auth/roles :auth/login)
     :post {:parameters
            {:body
             {:login string?
              :password string?}}
            :responses
            {200
             {:body
              {:identity
               {:login string?
                :created_at inst?}}}
             401
             {:body
              {:message string?}}}
            :handler
            (fn [{{{:keys [login password]} :body} :parameters
                  session :session}]
              (if-some [u (auth/authenticate-user login password)]
                (->
                 (response/ok
                  {:identity u})
                 (assoc :session (assoc session
                                        :identity
                                        u)))
                (response/unauthorized
                 {:message "Incorrect login or password."})))}}]
   ["/logout"
    {::auth/roles (auth/roles :auth/logout)
     :post {:handler
            (fn [_]
              (->
               (response/ok)
               (assoc :session nil)))}}]

   ["/register"
    {::auth/roles (auth/roles :account/register)
     :post {:parameters
            {:body
             {:login string?
              :password string?
              :confirm string?}}
            :responses
            {200
             {:body {:message string?}}
             400
             {:body {:message string?}}
             409
             {:body {:message string?}}}
            :handler
            (fn [{{{:keys [login password confirm]} :body} :parameters}]
              (if-not (= password confirm)
                (response/bad-request
                 {:message
                  "Password and Confirm do not match."})
                (try
                  (auth/create-user! login password)
                  (response/ok
                   {:message
                    "User registration successful. Please log in."})
                  (catch clojure.lang.ExceptionInfo e
                    (if (= (:guestbook/error-id (ex-data e))
                           ::auth/duplicate-user)
                      (response/conflict
                       {:message
                        "Registration failed! User with login already exists!"})
                      (throw e))))))}}]

   ["/message"
    {::auth/roles (auth/roles :message/create!)
     :post
     {:parameters
      {:body ;; Data Spec for Request body parameters
       {:name string?
        :message string?}}
      :responses
      {200
       {:body map?}
       400
       {:body map?}
       500
       {:errors map?}}
      :handler
      (fn [{{params :body} :parameters
            {:keys [identity]} :session}]
        (try
          (->> (msg/save-message! identity params)
               (assoc {:status :ok} :post)
               (response/ok))
          (catch Exception e
            (let [{id :guestbook/error-id
                   errors :errors} (ex-data e)]
              (case id
                :validation
                (response/bad-request {:errors errors})
               ;;else
                (response/internal-server-error
                 {:errors
                  {:server-error ["Failed to save message!"]}}))))))}}]])
