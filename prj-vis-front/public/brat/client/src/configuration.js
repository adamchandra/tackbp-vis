// -*- Mode: JavaScript; tab-width: 2; indent-tabs-mode: nil; -*-
// vim:set ft=javascript ts=2 sw=2 sts=2 cindent:
var Configuration = (function(window, undefined) {
    var abbrevsOn = true;
    var textBackgrounds = "striped";
    var svgWidth = '100%';
    var rapidModeOn = false;
    var confirmModeOn = true;
    var autorefreshOn = false;
    
    var visual = {
      margin: { x: 3, y: 2 },
      arcTextMargin: 2,
      boxSpacing: 4,
      curlyHeight: 6,
      arcSpacing: 8, //10; // TODO parameterize
      arcStartHeight: 25, //23; //25;
    }

    return {
      abbrevsOn: abbrevsOn,
      textBackgrounds: textBackgrounds,
      visual: visual,
      svgWidth: svgWidth,
      rapidModeOn: rapidModeOn,
      confirmModeOn: confirmModeOn,
      autorefreshOn: autorefreshOn,
    };
})(window);
