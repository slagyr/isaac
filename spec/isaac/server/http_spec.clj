(ns isaac.server.http-spec
  (:require
    [isaac.logger :as log]
    [isaac.server.http :as sut]
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

    (it "logs request received"
      (let [logged  (atom [])
            handler (sut/create-handler)]
        (with-redefs [log/log* (fn [level data _ _] (swap! logged conj {:level level :data data}))]
          (handler {:request-method :get :uri "/status"}))
        (should (some #(= :server/request-received (get-in % [:data :event])) @logged))))

    (it "logs response sent with status and latency"
      (let [logged  (atom [])
            handler (sut/create-handler)]
        (with-redefs [log/log* (fn [level data _ _] (swap! logged conj {:level level :data data}))]
          (handler {:request-method :get :uri "/status"}))
        (let [sent (first (filter #(= :server/response-sent (get-in % [:data :event])) @logged))]
          (should-not-be-nil sent)
          (should= 200 (get-in sent [:data :status]))
          (should-not-be-nil (get-in sent [:data :ms])))))

    (it "logs the request method and uri"
      (let [logged  (atom [])
            handler (sut/create-handler)]
        (with-redefs [log/log* (fn [level data _ _] (swap! logged conj {:level level :data data}))]
          (handler {:request-method :get :uri "/status"}))
        (let [received (first (filter #(= :server/request-received (get-in % [:data :event])) @logged))]
          (should= :get (get-in received [:data :method]))
          (should= "/status" (get-in received [:data :uri])))))

    (it "logs request-failed with ex-class and error-message on exception"
      (let [logged  (atom [])
            handler (sut/create-handler (fn [_] (throw (Exception. "handler exploded"))))]
        (with-redefs [log/log* (fn [level data _ _] (swap! logged conj {:level level :data data}))]
          (handler {:request-method :get :uri "/boom"}))
        (let [err (first (filter #(= :error (:level %)) @logged))]
          (should-not-be-nil err)
          (should= :server/request-failed (get-in err [:data :event]))
          (should= 500 (get-in err [:data :status]))
          (should-not-be-nil (get-in err [:data :ex-class]))
          (should= "handler exploded" (get-in err [:data :error-message])))))

    (it "returns 500 response on handler exception"
      (let [handler (sut/create-handler (fn [_] (throw (Exception. "oops"))))]
        (let [response (handler {:request-method :get :uri "/boom"})]
          (should= 500 (:status response)))))

    )

  )
