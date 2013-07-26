package net.openreview.util

object colors {
  import java.awt.Color

  /**
    * Converts a type Color to a hex string
    * in the format "#RRGGBB"
    */
  def colorToHex(color: Color): String = {
    var colorstr = "#";
    // Red
    var str = Integer.toHexString(color.getRed());
    if (str.length() > 2)
      str = str.substring(0, 2);
    else if (str.length() < 2)
      colorstr += "0" + str;
    else
      colorstr += str;

    // Green
    str = Integer.toHexString(color.getGreen());
    if (str.length() > 2)
      str = str.substring(0, 2);
    else if (str.length() < 2)
      colorstr += "0" + str;
    else
      colorstr += str;

    // Blue
    str = Integer.toHexString(color.getBlue());
    if (str.length() > 2)
      str = str.substring(0, 2);
    else if (str.length() < 2)
      colorstr += "0" + str;
    else
      colorstr += str;

    return colorstr;
  }


  def rgb(r:Int, g:Int, b:Int): String = {
    colorToHex(new Color(r, g, b))
  }

}
