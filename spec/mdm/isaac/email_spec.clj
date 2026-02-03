(ns mdm.isaac.email-spec
  (:require
    [mdm.isaac.aws :as aws]
    [mdm.isaac.config :as config]
    [mdm.isaac.email :as sut]
    [speclj.core :refer :all])
  (:import (jakarta.mail Session Message$RecipientType)
           (jakarta.mail.internet MimeMessage MimeMultipart)
           (java.io ByteArrayInputStream ByteArrayOutputStream)
           (software.amazon.awssdk.services.sesv2 SesV2Client)
           (software.amazon.awssdk.services.sesv2.model SendEmailRequest EmailContent RawMessage)
           (software.amazon.awssdk.core SdkBytes)))

(describe "Email"

  (with-stubs)

  (context "mime-message"

    (it "builds a session"
      (should (instance? Session (sut/make-session))))

    (it "escapes HTML characters for fallback rendering"
      (should= "foo &amp; bar &lt;b&gt;hi&lt;/b&gt;<br>next"
               (sut/escape-html "foo & bar <b>hi</b>\nnext")))

    (it "falls back to HTML from plain text"
      (let [body        {:text "This is <b>escaped</b> text"}
            part        (sut/->body body)
            alt-content (.getContent part)
            html-part   (.getBodyPart alt-content 1)]
        (should= "text/html; charset=UTF-8" (.getContentType html-part))
        (should-contain "&lt;b&gt;escaped&lt;/b&gt;" (.getContent html-part))))

    (it "uses provided HTML when available"
      (let [body        {:text "Plain" :html "<h1>Custom</h1>"}
            part        (sut/->body body)
            alt-content (.getContent part)
            html-part   (.getBodyPart alt-content 1)]
        (should= "<h1>Custom</h1>" (.getContent html-part))))

    (it "supports multiple TO recipients"
      (let [email (sut/mime-message {:from    "me@example.com"
                                     :to      "a@a.com,b@b.com"
                                     :subject "Multi"
                                     :text    "Hello"})
            msg   (MimeMessage. (sut/make-session)
                                (ByteArrayInputStream. (.toByteArray email)))]
        (should= 2 (count (.getRecipients msg Message$RecipientType/TO)))))

    (it "creates a full mime message with content"
      (let [attachment {:name         "test.pdf"
                        :content      (.getBytes "PDF content")
                        :content-type "application/pdf"}
            out-stream (sut/mime-message {:from        "me@example.com"
                                          :to          "you@example.com"
                                          :subject     "Here’s your file"
                                          :body        "Not used"
                                          :attachments [attachment]})
            mime-msg   (MimeMessage. (sut/make-session)
                                     (ByteArrayInputStream. (.toByteArray out-stream)))]
        (should= "me@example.com" (-> mime-msg .getFrom first str))
        (should= "you@example.com" (-> mime-msg (.getRecipients Message$RecipientType/TO) first str))
        (should= "Here’s your file" (.getSubject mime-msg))
        (let [content (.getContent mime-msg)]
          (should (instance? MimeMultipart content))
          (should= 2 (.getCount content)))))

    (it "builds RawMessage from mime-message bytes"
      (let [dummy-bytes  (.getBytes "dummy email")
            dummy-stream (doto (ByteArrayOutputStream.)
                           (.write dummy-bytes))]
        (with-redefs [sut/mime-message (stub :mime {:return dummy-stream})]
          (let [dummy-address {:to "x@y.com"}
                msg           (sut/->raw-message dummy-address)]
            (should-have-invoked :mime {:with [dummy-address]})
            (should (instance? RawMessage msg))
            (should (instance? SdkBytes (.data msg)))))))

    (it "wraps RawMessage in EmailContent"
      (let [raw     (-> (RawMessage/builder)
                        (.data (SdkBytes/fromByteArray (.getBytes "abc")))
                        .build)
            content (sut/->email-content raw)]
        (should (instance? EmailContent content))
        (should= raw (.raw content))))

    (it "builds SendEmailRequest from EmailContent"
      (let [dummy-content (-> (EmailContent/builder)
                              (.raw (-> (RawMessage/builder)
                                        (.data (SdkBytes/fromByteArray (.getBytes "abc")))
                                        .build))
                              .build)
            request       (sut/->email-request dummy-content)]
        (should (instance? SendEmailRequest request))
        (should= dummy-content (.content request))))
    )

  (context "SES client"

    (it "builds an SES client with correct region"
      (with-redefs [config/development? true
                    aws/access-key      "access-key"
                    aws/secret-key      "test-secret"]
        (should (instance? SesV2Client (sut/build-ses-client))))))
  )
