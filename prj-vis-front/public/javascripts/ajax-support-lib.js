// NOPUSH with lift stuff attached


var liftAjax = {
    n: 3,
    lift_ajaxQueue: [],
    lift_ajaxInProcess: null,
    lift_doCycleQueueCnt: 0,
    lift_ajaxShowing: false,
    lift_ajaxRetryCount: 3, // """ + (LiftRules.ajaxRetryCount openOr 3) + """,

    lift_ajaxHandler: function(theData, theSuccess, theFailure, responseType){
	      var toSend = {retryCnt: 0};
	      toSend.when = (new Date()).getTime();
	      toSend.theData = theData;
	      toSend.onSuccess = theSuccess;
	      toSend.onFailure = theFailure;
	      toSend.responseType = responseType;
	      toSend.version = liftAjax.lift_ajaxVersion++;

        // Make sure we wrap when we hit JS max int.
        var version = liftAjax.lift_ajaxVersion
        if ((version - (version + 1) != -1) || (version - (version - 1) != 1))
            liftAjax.lift_ajaxVersion = 0;

	      if (liftAjax.lift_uriSuffix) {
	          theData += '&' + liftAjax.lift_uriSuffix;
	          toSend.theData = theData;
	          liftAjax.lift_uriSuffix = undefined;
	      }

	      liftAjax.lift_ajaxQueue.push(toSend);
	      liftAjax.lift_ajaxQueueSort();
	      liftAjax.lift_doCycleQueueCnt++;
	      liftAjax.lift_doAjaxCycle();
	      return false; // buttons in forms don't trigger the form

    }
}

//         lift_uriSuffix: undefined,
// 
//         lift_logError: function(msg) {
//             // TODO dev/prod versions
//             // LiftRules.jsLogFunc.map(_(JsVar("msg")).toJsCmd) openOr ""
//             lift_defaultLogError(msg)
//         },
// 
//         lift_defaultLogError: function(msg) {
//             if (console && typeof console.error == 'function')
//                 console.error(msg);
//             else
//                 alert(msg);
//         },
//     
//         lift_ajaxQueueSort: function() {
//             liftAjax.lift_ajaxQueue.sort(function (a, b) {return a.when - b.when;});
//         },
// 
//         lift_defaultFailure: function() {
//             // """ + (LiftRules.ajaxDefaultFailure.map(_().toJsCmd) openOr "") + """
//             // () => JsCmds.Alert(S.?("ajax.error"))
//             alert("ajax.error")
//         },
// 
//         lift_startAjax: function() {
//             liftAjax.lift_ajaxShowing = true;
//             // """ + (LiftRules.ajaxStart.map(_().toJsCmd) openOr "") + """
//             console("starting ajax..")
//         },
// 
//         lift_endAjax: function() {
//             liftAjax.lift_ajaxShowing = false;
//             // """ + (LiftRules.ajaxEnd.map(_().toJsCmd) openOr "") + """
//             console("...ending ajax")
//         },
// 
//         lift_testAndShowAjax: function() {
//             if (liftAjax.lift_ajaxShowing && liftAjax.lift_ajaxQueue.length == 0 && liftAjax.lift_ajaxInProcess == null) {
//                 liftAjax.lift_endAjax();
//             } else if (!liftAjax.lift_ajaxShowing && (liftAjax.lift_ajaxQueue.length > 0 || liftAjax.lift_ajaxInProcess != null)) {
//                 liftAjax.lift_startAjax();
//             }
//         },
// 
//         lift_traverseAndCall: function(node, func) {
//             if (node.nodeType == 1) func(node);
//             var i = 0;
//             var cn = node.childNodes;
// 
//             for (i = 0; i < cn.length; i++) {
//                 liftAjax.lift_traverseAndCall(cn.item(i), func);
//             }
//         },
// 
//         lift_successRegisterGC: function() {
//             // setTimeout("liftAjax.lift_registerGC()", """ + LiftRules.liftGCPollingInterval + """);
//             setTimeout("liftAjax.lift_registerGC()", 75*1000); // 75 seconds
//         },
// 
//         lift_failRegisterGC: function() {
//             // setTimeout("liftAjax.lift_registerGC()", """ + LiftRules.liftGCFailureRetryTimeout + """);
//             setTimeout("liftAjax.lift_registerGC()", 15*1000); // 15 seconds
//         },
// 
// 
//         // ++        lift_registerGC: function() {
//         // ++            var data = "__lift__GC=_",
//         // ++            version = null;
//         // ++            """ + LiftRules.jsArtifacts.ajax(AjaxInfo(JE.JsRaw("data"),
//         // ++"POST",
//         // ++LiftRules.ajaxPostTimeout,
//         // ++false, "script",
//         // ++Full("liftAjax.lift_successRegisterGC"), Full("liftAjax.lift_failRegisterGC"))) +
//         // ++"""
//         // ++        },
// 
// 
//         lift_sessionLost: function() {
//             location.reload();
//         },
// 
//         lift_doAjaxCycle: function() {
//             if (liftAjax.lift_doCycleQueueCnt > 0) liftAjax.lift_doCycleQueueCnt--;
//             var queue = liftAjax.lift_ajaxQueue;
//             if (queue.length > 0) {
//                 var now = (new Date()).getTime();
//                 if (liftAjax.lift_ajaxInProcess == null && queue[0].when <= now) {
//                     var aboutToSend = queue.shift();
// 
//                     liftAjax.lift_ajaxInProcess = aboutToSend;
// 
//                     var successFunc = function(data) {
//                         liftAjax.lift_ajaxInProcess = null;
//                         if (aboutToSend.onSuccess) {
//                             aboutToSend.onSuccess(data);
//                         }
//                         liftAjax.lift_doCycleQueueCnt++;
//                         liftAjax.lift_doAjaxCycle();
//                     };
// 
//                     var failureFunc = function() {
//                         liftAjax.lift_ajaxInProcess = null;
//                         var cnt = aboutToSend.retryCnt;
// 
//                         if (arguments.length == 3 && arguments[1] == 'parsererror') {
//                             liftAjax.lift_logError('The server call succeeded, but the returned Javascript contains an error: '+arguments[2])
//                         } else if (cnt < liftAjax.lift_ajaxRetryCount) {
//                             aboutToSend.retryCnt = cnt + 1;
//                             var now = (new Date()).getTime();
//                             aboutToSend.when = now + (1000 * Math.pow(2, cnt));
//                             queue.push(aboutToSend);
//                             liftAjax.lift_ajaxQueueSort();
//                         } else {
//                             if (aboutToSend.onFailure) {
//                                 aboutToSend.onFailure();
//                             } else {
//                                 liftAjax.lift_defaultFailure();
//                             }
//                         }
//                         liftAjax.lift_doCycleQueueCnt++;
//                         liftAjax.lift_doAjaxCycle();
//                     };
// 
//                     if (aboutToSend.responseType != undefined &&
//                         aboutToSend.responseType != null &&
//                         aboutToSend.responseType.toLowerCase() === "json") {
//                         liftAjax.lift_actualJSONCall(aboutToSend.theData, successFunc, failureFunc);
//                     } else {
//                         var theData = aboutToSend.theData,
//                         version = aboutToSend.version;
// 
//                         liftAjax.lift_actualAjaxCall(theData, version, successFunc, failureFunc);
//                     }
//                 }
//             }
// 
//             liftAjax.lift_testAndShowAjax();
//             if (liftAjax.lift_doCycleQueueCnt <= 0) liftAjax.lift_doCycleIn200()
//         },
// 
//         lift_doCycleIn200: function() {
//             liftAjax.lift_doCycleQueueCnt++;
//             setTimeout("liftAjax.lift_doAjaxCycle();", 200);
//         },
// 
//         lift_ajaxVersion: 0,
// 
//         addPageNameAndVersionWithGC: function(url, version) {
//             var replacement = '""" + LiftRules.ajaxPath + """/'+lift_page;
//             if (version!=null)
//                 replacement += ('-'+version.toString(36)) + (liftAjax.lift_ajaxQueue.length > 35 ? 35 : liftAjax.lift_ajaxQueue.length).toString(36);
//             return url.replace('""" + LiftRules.ajaxPath + """', replacement);
//         },
// 
//         addPageNameAndVersionWithoutGC: function(url, version) {
//             "return url;"
//         },
// 
// 
//         lift_actualAjaxCall: function(data, version, onSuccess, onFailure) {
//             $.ajax({
//                 url: "/ajax/path/todo",
//                 type: "post",
//                 data: data,
//                 dataType: 'json',
//                 cache: false,
//                 contentType: false,
//                 processData: false,
//                 timeout: 5000, // ajax post timeout
//                 cache: false, // TODO parameterize
//                 statusCode: {
//                     200: function(data) {
//                         onSuccess(data)
//                     },
//                     500: function() {
//                         onFailure()
//                     },
//                     404: function() {
//                         onFailure()
//                     }
//                 },
//                 complete: function() {
//                     // button.removeAttr('disabled')
//                 }
//             });
// 
//             //  toJson(ajaxInfo) = 
//             //   private def toJson(info: AjaxInfo, server: String, path: String => JsExp): String =
//             //     (("url : " + path(server).toJsCmd) ::
//             //             "data : " + info.data.toJsCmd ::
//             //             ("type : " + info.action.encJs) ::
//             //             ("dataType : " + info.dataType.encJs) ::
//             //             "timeout : " + info.timeout ::
//             //             "cache : " + info.cache :: Nil) ++
//             //             info.successFunc.map("success : " + _).toList ++
//             //             info.failFunc.map("error : " + _).toList mkString ("{ ", ", ", " }")
//             // }
// 
//             //LiftRules.jsArtifacts.ajax(AjaxInfo(
//             //  data: JsExp = JE.JsRaw("data"),
//             //  action = "POST",
//             //  timeout = LiftRules.ajaxPostTimeout,
//             //  cache = false, 
//             //  dataType = "script",
//             //  successfn = Full("onSuccess"), 
//             //  failefn = Full("onFailure"))) +
//         },
// 
//         lift_actualJSONCall: function(data, onSuccess, onFailure) {
//             var version = null;
//             $.ajax({
//                 url: "/ajax/path/todo",
//                 type: "post",
//                 data: data,
//                 dataType: 'json',
//                 cache: false,
//                 contentType: false,
//                 processData: false,
//                 timeout: 5000, // ajax post timeout
//                 cache: false, // TODO parameterize
//                 statusCode: {
//                     200: function(data) {
//                         onSuccess(data)
//                     },
//                     500: function() {
//                         onFailure()
//                     },
//                     404: function() {
//                         onFailure()
//                     }
//                 },
//                 complete: function() {
//                     // button.removeAttr('disabled')
//                 }
//             });
// 
// 
//             //          """ +
//             //          LiftRules.jsArtifacts.ajax(AjaxInfo(JE.JsRaw("data"),
//             //            "POST",
//             //            LiftRules.ajaxPostTimeout,
//             //            false, "json",
//             //            Full("onSuccess"), Full("onFailure"))) +
//             //          """
//         }
//     };
// 
//     window.liftUtils = {
//         lift_blurIfReturn: function(e) {
//             var code;
//             if (!e) var e = window.event;
//             if (e.keyCode) code = e.keyCode;
//             else if (e.which) code = e.which;
// 
//             var targ;
// 
//             if (e.target) targ = e.target;
//             else if (e.srcElement) targ = e.srcElement;
//             if (targ.nodeType == 3) // defeat Safari bug
//                 targ = targ.parentNode;
//             if (code == 13) {targ.blur(); return false;} else {return true;};
//         }
//     };



//     """ + LiftRules.jsArtifacts.onLoad(new JsCmd() {def toJsCmd = "liftAjax.lift_doCycleIn200();"}).toJsCmd)



        //    $.ajax({
        //      url: url,
        //      type: method,
        //      data: data,
        //      dataType: 'json',
        //      cache: false,
        //      contentType: false,
        //      processData: false,
        //      statusCode: {
        //        200: function(data) {
        //            $this.find('input[type=text],textarea').val('')
        //            processData(data, $this)
        //        },
        //        500: function() {
        //          $this.trigger('bootstrap-ajax:error', [$this, 500])
        //        },
        //        404: function() {
        //          $this.trigger('bootstrap-ajax:error', [$this, 404])
        //        }
        //      },
        //      complete: function() {
        //        button.removeAttr('disabled')
        //      }
        //    })


