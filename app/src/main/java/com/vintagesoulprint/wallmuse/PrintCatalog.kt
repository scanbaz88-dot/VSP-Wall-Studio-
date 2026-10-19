package com.vintagesoulprint.wallmuse

/** All dimensions are artwork openings, in centimetres, not outside frame dimensions. */
object PrintCatalog {
    data class Size(val name:String,val widthCm:Float,val heightCm:Float)
    private val us=listOf(
        4.0 to 6.0,5.0 to 7.0,6.0 to 8.0,8.0 to 10.0,8.0 to 12.0,8.5 to 11.0,9.0 to 12.0,
        11.0 to 14.0,11.0 to 17.0,12.0 to 16.0,12.0 to 18.0,12.0 to 24.0,13.0 to 19.0,
        14.0 to 18.0,15.0 to 20.0,16.0 to 20.0,16.0 to 24.0,18.0 to 24.0,18.0 to 27.0,
        20.0 to 24.0,20.0 to 28.0,20.0 to 30.0,22.0 to 28.0,24.0 to 30.0,24.0 to 36.0,
        27.0 to 40.0,30.0 to 40.0,30.0 to 45.0,36.0 to 48.0
    )
    private val metric=listOf(
        10.0 to 15.0,13.0 to 18.0,15.0 to 20.0,18.0 to 24.0,20.0 to 25.0,20.0 to 30.0,
        21.0 to 29.7,29.7 to 42.0,24.0 to 30.0,25.0 to 35.0,28.0 to 35.0,30.0 to 40.0,30.0 to 45.0,
        40.0 to 50.0,40.0 to 60.0,42.0 to 59.4,45.0 to 60.0,50.0 to 60.0,50.0 to 70.0,
        59.4 to 84.1,60.0 to 80.0,60.0 to 90.0,61.0 to 91.0,70.0 to 100.0,80.0 to 120.0,84.1 to 118.9
    )
    private val squares=listOf(5,6,8,10,12,16,20,24,30,36)
    val categories:List<String> get()=if(java.util.Locale.getDefault().language=="tr")
        listOf("Dikey · inç","Yatay · inç","Dikey · cm","Yatay · cm","Kare · inç","Kare · cm","Özel ölçü gir…")
        else listOf("Portrait · inches","Landscape · inches","Portrait · cm","Landscape · cm",
            "Square · inches","Square · cm","Custom size…")
    fun inCategory(category:Int):List<Size> = when(category){
        0->us.map{Size("${num(it.first)} × ${num(it.second)} in",it.first.toFloat()*2.54f,it.second.toFloat()*2.54f)}
        1->us.map{Size("${num(it.second)} × ${num(it.first)} in",it.second.toFloat()*2.54f,it.first.toFloat()*2.54f)}
        2->metric.map{Size("${num(it.first)} × ${num(it.second)} cm",it.first.toFloat(),it.second.toFloat())}
        3->metric.map{Size("${num(it.second)} × ${num(it.first)} cm",it.second.toFloat(),it.first.toFloat())}
        4->squares.map{Size("$it × $it in",it*2.54f,it*2.54f)}
        5->squares.map{Size("$it × $it cm",it.toFloat(),it.toFloat())}
        else->emptyList()
    }
    fun num(n:Number):String = n.toString().removeSuffix(".0")
}
