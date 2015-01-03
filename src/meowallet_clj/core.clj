(ns meowallet-clj.core
  (:use [slingshot.slingshot :only [throw+ try+]]))

(require '[clj-http.client :as client])
(require '[cheshire.core :as json])
(require '[ring.util.request :as ring-req])

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


(defn mw-post 
  [base-url k rsc params]
  (try+ 
    (let [b (if (map? params)
                  (json/generate-string params)
                  params
                 )]
          (client/post (str base-url rsc)
               {:headers {"authorization" (str "WalletPT " k)}
                :content-type :json
                :body b}))
    (catch Object e
      ;;; TODO something more useful here
      (println (str "mw post exception " e)))
    )
)

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
  (fn [{:keys [headers body request-method] :as request}]
    (let [bodystr (ring-req/body-string request)]
       (if-not (and (= request-method :post) (= (headers "content-type") "application/json"))
   {:status 400
   :header {:content-type "text/plain"}
   :body "my bad request"}
         (if (callback-valid? base-url k bodystr)
           (if (some? (cb (json/parse-string bodystr)))
             good-req-response
             bad-req-response
           ))))))


