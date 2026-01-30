(ns mdm.isaac.email
  (:require
    [c3kit.apron.log :as log]
    [clojure.string :as str]
    [mdm.isaac.aws :as aws]
    [mdm.isaac.config :as config])
  (:import
    (jakarta.activation DataHandler)
    (jakarta.mail Message$RecipientType Session)
    (jakarta.mail.internet InternetAddress MimeBodyPart MimeMessage MimeMultipart)
    (jakarta.mail.util ByteArrayDataSource)
    (java.io ByteArrayOutputStream)
    (java.nio ByteBuffer)
    (java.util Properties)
    (software.amazon.awssdk.auth.credentials AwsBasicCredentials InstanceProfileCredentialsProvider StaticCredentialsProvider)
    (software.amazon.awssdk.core SdkBytes)
    (software.amazon.awssdk.regions Region)
    (software.amazon.awssdk.services.sesv2 SesV2Client)
    (software.amazon.awssdk.services.sesv2.model EmailContent RawMessage SendEmailRequest)))

(defn ^Session make-session []
  (Session/getDefaultInstance (Properties.)))

(defmulti client-send-email (fn [conf email] (:client conf)))

(defn escape-html [s]
  (-> s
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")
      (str/replace "\"" "&quot;")
      (str/replace "'" "&#x27;")
      (str/replace "\n" "<br>")))

(defn ->body [{:keys [text html]}]
  (let [text      (or text "")
        html      (or html (str "<p>" (escape-html text) "</p>"))
        text-part (doto (MimeBodyPart.)
                    (.setText text "UTF-8"))
        html-part (doto (MimeBodyPart.)
                    (.setContent html "text/html; charset=UTF-8")
                    (.setHeader "Content-Type" "text/html; charset=UTF-8"))
        alt-part  (MimeMultipart. "alternative")]
    (.addBodyPart alt-part text-part)
    (.addBodyPart alt-part html-part)
    (doto (MimeBodyPart.)
      (.setContent alt-part))))

(defn ->part [{:keys [content name content-type]}]
  (let [part   (MimeBodyPart.)
        source (ByteArrayDataSource. content content-type)]
    (doto part
      (.setDataHandler (DataHandler. source))
      (.setFileName name)
      (.setHeader "Content-Type" content-type))))

(defn attachments->multipart [body attachments]
  (let [mixed-part (MimeMultipart. "mixed")]
    (.addBodyPart mixed-part (->body body))
    (doseq [attachment attachments]
      (let [attach-part (->part attachment)]
        (.addBodyPart mixed-part attach-part)))
    mixed-part))

(defn mime-message [{:keys [from to subject text html attachments]}]
  (let [session   (make-session)
        msg       (MimeMessage. session)
        body      {:text text :html html}
        multipart (attachments->multipart body attachments)
        out       (ByteArrayOutputStream.)]
    (doto msg
      (.setFrom (InternetAddress. from))
      (.setRecipients Message$RecipientType/TO (InternetAddress/parse to))
      (.setSubject subject)
      (.setContent multipart)
      (.writeTo out))
    out))

; TODO - consolidate with airworthy.aws
(defn make-credentials-provider []
  (if config/development?
    (StaticCredentialsProvider/create
      (AwsBasicCredentials/create aws/access-key aws/secret-key))
    (InstanceProfileCredentialsProvider/create)))

(defn build-ses-client []
  (-> (SesV2Client/builder)
      (.region Region/US_WEST_2)
      (.credentialsProvider (make-credentials-provider))
      .build))

(defn ->raw-message [email]
  (-> (RawMessage/builder)
      (.data (SdkBytes/fromByteBuffer
               (ByteBuffer/wrap (.toByteArray (mime-message email)))))
      .build))

(defn ->email-content [raw-message]
  (-> (EmailContent/builder)
      (.raw raw-message)
      .build))

(defn ->email-request [email-content]
  (-> (SendEmailRequest/builder)
      (.content email-content)
      .build))

(defmethod client-send-email :ses [_ {:keys [to subject] :as email}]
  (log/info "SES: sending email to:" to "-" subject)
  (try
    (let [client  (build-ses-client)
          raw-msg (->raw-message email)
          content (->email-content raw-msg)
          request (->email-request content)]
      (.sendEmail client request)
      (log/debug "SES email sent"))
    (catch Exception e
      (log/error "Email error:")
      (log/error e))))

(defmethod client-send-email :to-log [_ {:keys [from to cc bcc subject text html]}]
  (let [divider (apply str (repeat 80 "="))]
    (log/report (str divider "\n"
                     "[To]      " to "\n"
                     "[Cc]      " cc "\n"
                     "[Bcc]     " bcc "\n"
                     "[From]    " from "\n"
                     "[Subject] " subject "\n"
                     "\nText Body:\n"
                     text
                     "\n\nHTML Body:\n"
                     html
                     divider))))

(defn send! [email]
  (client-send-email (:email config/active) email))
