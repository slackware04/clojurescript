;;  Copyright (c) Rich Hickey. All rights reserved.
;;  The use and distribution terms for this software are covered by the
;;  Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;;  which can be found in the file epl-v10.html at the root of this distribution.
;;  By using this software in any fashion, you are agreeing to be bound by
;;  the terms of this license.
;;  You must not remove this notice, or any other, from this software.

(ns ^{:doc "Network communication library, wrapping goog.net.
Includes a common API over XhrIo, CrossPageChannel, and Websockets."
      :author "Bobby Calderwood and Alex Redington"}
  clojure.browser.net
  (:require [clojure.browser.event :as event]
            [goog.net.XhrIo :as gxhrio]
            [goog.net.EventType :as gnet-event-type]
            [goog.net.xpc.CfgFields :as gxpc-config-fields]
            [goog.net.xpc.CrossPageChannel :as xpc]
            #_[goog.net.WebSocket :as gwebsocket]
            [goog.json :as gjson]))

(def *timeout* 10000)

(def event-types
  (into {}
        (map
         (fn [[k v]]
           [(keyword (. k (toLowerCase)))
            v])
         (merge
          (js->clj goog.net.EventType)))))

(defprotocol IConnection
  (connect
    [this]
    [this opt1]
    [this opt1 opt2]
    [this opt1 opt2 opt3])
  (transmit
    [this opt]
    [this opt opt2]
    [this opt opt2 opt3]
    [this opt opt2 opt3 opt4]
    [this opt opt2 opt3 opt4 opt5])
  (close [this]))

(extend-type goog.net.XhrIo

  IConnection
  (transmit
    ([this uri]
       (transmit this uri "GET"  nil nil *timeout*))
    ([this uri method]
       (transmit this uri method nil nil *timeout*))
    ([this uri method content]
       (transmit this uri method content nil *timeout*))
    ([this uri method content headers]
       (transmit this uri method content headers *timeout*))
    ([this uri method content headers timeout]
       (.setTimeoutInterval this timeout)
       (.send this uri method content headers)))


  event/EventType
  (event-types [this]
    (into {}
          (map
           (fn [[k v]]
             [(keyword (. k (toLowerCase)))
              v])
           (merge
            (js->clj goog.net.EventType))))))

;; TODO jQuery/sinatra/RestClient style API: (get [uri]), (post [uri payload]), (put [uri payload]), (delete [uri])

(def xpc-config-fields
  (into {}
        (map
         (fn [[k v]]
           [(keyword (. k (toLowerCase)))
            v])
         (js->clj goog.net.xpc.CfgFields))))

(defn xhr-connection
  "Returns an XhrIo connection"
  []
  (goog.net.XhrIo.))

(defprotocol ICrossPageChannel
  (register-service [this service-name fn] [this service-name fn encode-json?]))

(extend-type goog.net.xpc.CrossPageChannel

  ICrossPageChannel
  (register-service
    ([this service-name fn]
       (register-service this service-name fn false))
    ([this service-name fn encode-json?]
       (.registerService this (name service-name) fn encode-json?)))

  IConnection
  (connect
    ([this]
       (connect this nil))
    ([this on-connect-fn]
       (.connect this on-connect-fn))
    ([this on-connect-fn config-iframe-fn]
       (connect this on-connect-fn config-iframe-fn (.body js/document)))
    ([this on-connect-fn config-iframe-fn iframe-parent]
       (.createPeerIframe this iframe-parent config-iframe-fn)
       (.connect this on-connect-fn)))

  (transmit [this service-name payload]
    (.send this (name service-name) payload))

  (close [this]
    (.close this ())))

(defn xpc-connection
  "When passed with a config hash-map, returns a parent
  CrossPageChannel object. Keys in the config hash map are downcased
  versions of the goog.net.xpc.CfgFields enum keys,
  e.g. goog.net.xpc.CfgFields.PEER_URI becomes :peer_uri in the config
  hash.

  When passed with no args, creates a child CrossPageChannel object,
  and the config is automatically taken from the URL param 'xpc', as
  per the CrossPageChannel API."
  ([]
     (when-let [config (.getParameterValue
                        (goog.Uri. (.href (.location js/window)))
                        "xpc")]
       (goog.net.xpc.CrossPageChannel. (gjson/parse config))))
  ([config]
     (goog.net.xpc.CrossPageChannel.
      (.strobj (reduce (fn [sum [k v]]
                         (when-let [field (get xpc-config-fields k)]
                           (assoc sum field v)))
                       {}
                       config)))))

;; WebSocket is not supported in the 3/23/11 release of Google
;; Closure, but will be included in the next release.

#_(defprotocol IWebSocket
    (open? [this]))

#_(extend-type goog.net.WebSocket

  IWebSocket
  (open? [this]
    (.isOpen this ()))

  IConnection
  (connect
    ([this url]
       (connect this url nil))
    ([this url protocol]
       (.open this url protocol)))

  (transmit [this message]
    (.send this message))

  (close [this]
    (.close this ()))

  event/EventType
  (event-types [this]
    (into {}
          (map
           (fn [[k v]]
             [(keyword (. k (toLowerCase)))
              v])
           (merge
            (js->clj goog.net.WebSocket/EventType))))))

#_(defn websocket-connection
  ([]
     (websocket-connection nil nil))
  ([auto-reconnect?]
     (websocket-connection auto-reconnect? nil))
  ([auto-reconnect? next-reconnect-fn]
     (goog.net.WebSocket. auto-reconnect? next-reconnect-fn)))