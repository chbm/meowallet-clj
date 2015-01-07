(defproject org.clojars.chbm/meowallet-clj "1.0.0"
  :description "MEO Wallet library"
  :url "https://github.com/chbm/meowallet-clj"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [clj-http "1.0.1"]
                 [cheshire "5.4.0"]
                 [slingshot "0.12.1"]
                 [ring/ring-core "1.3.2"]]
  :profiles { :test {:dependencies [[ring/ring-mock "0.2.0"]] }
              :repl {:dependencies [[ring/ring-mock "0.2.0"]] }}
  )
