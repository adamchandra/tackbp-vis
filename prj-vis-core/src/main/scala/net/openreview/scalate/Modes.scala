package net.openreview.scalate

sealed trait ScalateMode
case object LayoutMode extends ScalateMode 
case object DevMode extends ScalateMode 
case object ProductionMode extends ScalateMode 
case object PrecompileMode extends ScalateMode 
