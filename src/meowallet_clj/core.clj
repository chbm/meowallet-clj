(ns meowallet-clj.core
  (:use [slingshot.slingshot :only [throw+ try+]]))

(require '[clj-http.client :as client])
(require '[cheshire.core :as json])
(import '(java.io StringReader BufferedReader))

(defmacro def- 
  [symbol & body] 
  `(def ^:private ~symbol ~@body))



(defn with-key-on [env key]
  (let [baseurl (if (= env :production)
                  "https://services.wallet.pt/api/v2/" 
                  "https://services.sandbox.meowallet.pt/api/v2/")]
      (fn [f & body] 
        (apply f baseurl key body))
    ))

(defn map-checkout 
  [{:keys [url_confirm url_cancel amount currency client items] :or {currency "EUR"}}]
  {:pre [(some? amount)
         (= currency "EUR")]}
  (merge {:payment (merge {:amount amount :currency currency} 
                          (if (map? client) {:client client})
                          (if (vector? items) {:items items})
                          )}
         (if (some? url_confirm) {:url_confirm url_confirm})
         (if (some? url_cancel) {:url_confirm url_cancel}) )
  )


(defn mw-post [base-url k rsc params]
  (try+
    (let [resp (client/post (str base-url rsc)
               {:headers {:Authorization (str "WalletPT " k)}
                :content-type :json
                :body (json/generate-string params)})]
            resp)
    (catch Object e
      ;;; TODO something more useful here
      (println e))
    ))

(defn- mw-parse-response 
  [resp]
  (if (map? resp)
    (json/parse-string (resp :body))
    nil
    )
  )

(defn start-checkout 
  "create a new checkout and return the checkout id and continuation url"
  [base-url k params]
  (let [resp (->> params
                (map-checkout)
                (mw-post base-url k "checkout")
                (mw-parse-response))]
    resp
  )
)


(defn- callback-valid? [base-url k thebody]
  (let [resp (mw-post base-url k "callback/verify" thebody)]
    (if (map? resp)
      (= (resp :status) 200)
      nil
    )
  ))

(def- bad-req-response
  {:status 400
   :header {:content-type "text/plain"}
   :body "bad request"}
  )

(def- good-req-response
  {:status 200
   :header {:content-type "text/plain"}
   :body "OK"}
  )

(defn register-callback
  [base-url k cb]
  (fn [{:keys [:header :body :request-method]} request]
       (if-not (and (= request-method :post) (= (header :content-type "application/json")))
         bad-req-response
         (if (callback-valid? base-url k body)
           (if (some? (cb (json/parse-string body)))
             good-req-response
             bad-req-response
           )))))


