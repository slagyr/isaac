(ns isaac.server.http-spec
  (:require
    [isaac.logger :as log]
    [isaac.server.http :as sut]
    [isaac.spec-helper :as helper]
    [speclj.core :refer :all]))

(describe "HTTP handler"

  (it "creates a handler function"
    (let [handler (sut/create-handler)]
      (should (fn? handler))))

  (it "handler responds to GET /status"
    (let [handler  (sut/create-handler)
          response (handler {:request-method :get :uri "/status"})]
      (should= 200 (:status response))))

  (it "handler returns 404 for unknown routes"
    (let [handler  (sut/create-handler)
          response (handler {:request-method :get :uri "/nope"})]
      (should= 404 (:status response))))

  (describe "request logging"

    (helper/with-captured-logs)

    (it "logs request received"
      (let [handler (sut/create-handler)]
        (handler {:request-method :get :uri "/status"})
        (should (some #(= :server/request-received (:event %)) @log/captured-logs))))

    (it "logs response sent with status and latency"
      (let [handler (sut/create-handler)]
        (handler {:request-method :get :uri "/status"})
        (let [sent (first (filter #(= :server/response-sent (:event %)) @log/captured-logs))]
          (should-not-be-nil sent)
          (should= 200 (:status sent))
          (should-not-be-nil (:ms sent)))))

    (it "logs the request method and uri"
      (let [handler (sut/create-handler)]
        (handler {:request-method :get :uri "/status"})
        (let [received (first (filter #(= :server/request-received (:event %)) @log/captured-logs))]
          (should= :get (:method received))
          (should= "/status" (:uri received)))))

    (it "logs request-failed with ex-class and error-message on exception"
      (let [handler (sut/create-handler (fn [_] (throw (Exception. "handler exploded"))))]
        (handler {:request-method :get :uri "/boom"})
        (let [err (first (filter #(= :error (:level %)) @log/captured-logs))]
          (should-not-be-nil err)
          (should= :server/request-failed (:event err))
          (should= 500 (:status err))
          (should-not-be-nil (:ex-class err))
          (should= "handler exploded" (:error-message err)))))

    (it "returns 500 response on handler exception"
      (let [handler (sut/create-handler (fn [_] (throw (Exception. "oops"))))]
        (let [response (handler {:request-method :get :uri "/boom"})]
          (should= 500 (:status response)))))

    )

  )
