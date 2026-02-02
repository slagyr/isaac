(ns mdm.isaac.server.routes-spec
  (:require [c3kit.apron.utilc :as utilc]
            [c3kit.wire.routes :as wire.routes]
            [c3kit.wire.spec-helper :as wire]
            [c3kit.wire.spec-helper :refer [test-route]]
            [compojure.core :refer [GET POST defroutes]]
            [mdm.isaac.server.routes :as routes]
            [mdm.isaac.spec-helper]
            [medley.core :as medley]
            [speclj.core :refer :all]))

(def args (atom :none))

(require '[mdm.isaac.sandbox.core])

(def get-response {:status 200 :body "blah" :headers {}})
(def post-response {:status 201 :body "blah" :headers {}})
(declare request)


;; region ----- antiforgery -----

(def invalid-anti-forgery-response {:status 403 :headers {"Content-Type" "text/html"} :body "<h1>Invalid anti-forgery token</h1>"})

(defroutes routes
  (GET "/blah" [] get-response)
  (POST "/blah" [] post-response)
  (POST "/signin/google-oauth" [] post-response)
  (POST "/signin/apple-oauth" [] post-response))

(def handler (routes/wrap-custom-anti-forgery routes))

(describe "custom anti forgery"

  (it "allows non-post request"
    (should= get-response (handler {:uri "/blah" :request-method :get})))

  (it "doesn't allow post request with missing anti forgery token"
    (let [response (handler {:uri "/blah" :request-method :post})]
      (should= 403 (:status response))
      (should-contain "Invalid anti-forgery token" (:body response))))

  (it "allows post requests with authorization header"
    (should= post-response (handler {:headers        {"authorization" "123"}
                                     :uri            "/blah"
                                     :request-method :post})))
  )

;; endregion ^^^^^ antiforgery ^^^^^

(describe "Routes"
  (with-stubs)
  (before (reset! args :none))
  (redefs-around [c3kit.wire.api/version (constantly "fake-api-version")
                  wire.routes/reload? (delay true)])

  (context "google"

    (with request {:request-method :post
                   :uri            "/signin/google-oauth"
                   :cookies        {"g_csrf_token" {:value "google-csrf"}}
                   :params         {:g_csrf_token "google-csrf"}
                   :body           "{}"})

    (it "not google signin uri"
      (let [request  (assoc @request :uri "/api/user/signin/goog")
            response (handler request)]
        (should= 403 (:status response))))

    (it "missing csrf cookie"
      (let [request  (medley/dissoc-in @request [:cookies "g_csrf_token" :value])
            response (handler request)]
        (should= 403 (:status response))))

    (it "missing csrf param"
      (let [request  (medley/dissoc-in @request [:params :g_csrf_token])
            response (handler request)]
        (should= 403 (:status response))))

    (it "csrf cookie and param do not match"
      (let [request  (assoc-in @request [:params :g_csrf_token] "blah")
            response (handler request)]
        (should= 403 (:status response))))

    (it "ok"
      (let [response (handler @request)]
        (should= post-response response)))
    )

  (context "apple"

    (with request {:request-method :post
                   :uri            "/signin/apple-oauth"
                   :params         {:state 22222}
                   :body           "{}"})

    (it "not apple signin uri"
      (let [request  (assoc @request :uri "/signin/appl")
            response (handler request)]
        (should= 403 (:status response))))

    (it "ok"
      (let [response (handler @request)]
        (should= post-response response)))
    )

  (context "ajax"
    (test-route "/ajax/user/signin" :post routes/handler mdm.isaac.user.ajax/ajax-signin)
    (test-route "/ajax/user/signup" :post routes/handler mdm.isaac.user.ajax/ajax-signup)
    (test-route "/ajax/user/forgot-password" :post routes/handler mdm.isaac.user.ajax/ajax-forgot-password)
    (test-route "/ajax/user/reset-password" :post routes/handler mdm.isaac.user.ajax/ajax-reset-password)
    )

  (it "not-found ajax"
    (let [response (routes/handler {:uri "/ajax/blah" :request-method :get})
          body     (-> response :body utilc/<-transit)]
      (should= 200 (:status response))
      (should= :fail (:status body))
      (should= :error (-> body :flash first :level))
      (should= "AJAX route not found: /ajax/blah" (-> body :flash first :text))))

  (context "web"
    (test-route "/" :get routes/handler mdm.isaac.server.layouts/web-rich-client)
    (test-route "/account/verify/UUID" :get routes/handler mdm.isaac.user.web/web-verify-account)
    (test-route "/recover/TOKEN" :get routes/handler mdm.isaac.user.web/web-reset-password)
    (test-route "/redirect" :get routes/handler c3kit.wire.destination/web-redirect)
    (test-route "/signin" :get routes/handler mdm.isaac.server.layouts/web-rich-client)
    (test-route "/signin/google-oauth" :post routes/handler mdm.isaac.user.web/web-google-oauth-login)
    (test-route "/sandbox/example-page" :get routes/handler mdm.isaac.sandbox.core/handler)
    (test-route "/signout" :any routes/handler mdm.isaac.user.web/web-signout)
    (test-route "/signup" :get routes/handler mdm.isaac.server.layouts/web-rich-client)
    (test-route "/user/ws" :get routes/handler mdm.isaac.user.web/websocket-open)
    (test-route "/terms" :get routes/handler mdm.isaac.server.layouts/web-rich-client)
    )

  (context "websocket"

    (wire/test-webs :user/fetch-data mdm.isaac.user.ws/ws-fetch-user-data)
    (wire/test-webs :goals/list mdm.isaac.ws/goals-list)
    (wire/test-webs :goals/add mdm.isaac.ws/goals-add)
    (wire/test-webs :goals/update mdm.isaac.ws/goals-update)
    (wire/test-webs :thoughts/recent mdm.isaac.ws/thoughts-recent)
    (wire/test-webs :thoughts/search mdm.isaac.ws/thoughts-search)
    (wire/test-webs :shares/unread mdm.isaac.ws/shares-unread)
    (wire/test-webs :shares/ack mdm.isaac.ws/shares-ack)

    )

  (it "not-found global - nil - handled by http"
    (let [response (routes/handler {:uri "/blah" :request-method :get})]
      (should-be-nil response)))

  )
