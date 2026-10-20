package com.vintagesoulprint.wallmuse

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Grid and gallery-wall placement templates work with normalized wall geometry.
 * A plan is a proposal, not proof of real-world physical fit.
 * Ratios and user-selected artwork aspect ratios are preserved by the canvas.
 */
object GalleryPlanner {
    enum class Layout { GRID, ROW, COLUMN, SALON, PYRAMID }
    data class Placement(val cx:Float,val cy:Float,val widthFraction:Float)
    fun arrange(count:Int,style:Layout):List<Placement>{
        if(count<=0)return emptyList()
        val n=count.coerceAtMost(12)
        if(n==1)return listOf(Placement(.5f,.46f,.34f))
        val width=when(style){
            Layout.ROW->min(.24f,.82f/n)
            Layout.COLUMN->.24f
            Layout.GRID->if(n<=4).28f else if(n<=7).20f else .15f
            Layout.SALON->if(n<=4).25f else if(n<=7).20f else .14f
            Layout.PYRAMID->if(n<=5).23f else if(n<=8).17f else .14f
        }.coerceIn(.08f,.38f)
        fun normalized(ix:Int,total:Int):Float=
            if(total==1).5f else .10f+.80f*ix/(total-1)
        return when(style){
            Layout.ROW->(0 until n).map{Placement(normalized(it,n),.47f,width)}
            Layout.COLUMN->(0 until n).map{Placement(.5f,normalized(it,n),width)}
            Layout.GRID->{
                val cols=ceil(sqrt(n.toDouble())).toInt()
                val rows=ceil(n.toDouble()/cols).toInt()
                (0 until n).map { index->
                    val row=index/cols
                    val inRow=min(cols,n-row*cols)
                    Placement(normalized(index%cols,inRow),normalized(row,rows),width)
                }
            }
            Layout.SALON->{
                val cols=if(n>=8)4 else 3
                val rows=ceil(n.toDouble()/cols).toInt()
                (0 until n).map{ index->
                    val row=index/cols
                    val inRow=min(cols,n-row*cols)
                    val x=normalized(index%cols,inRow)
                    val stagger=if((index+row)%2==0)-.035f else .035f
                    Placement(x,(normalized(row,rows)+stagger).coerceIn(.10f,.90f),
                        (width*if(index==0)1.15f else 1f).coerceAtMost(.38f))
                }
            }
            Layout.PYRAMID->{
                val cols=if(n>=7)4 else 3
                val rows=ceil(n.toDouble()/cols).toInt()
                (0 until n).map { index->
                    val row=index/cols
                    val inRow=min(cols,n-row*cols)
                    Placement(normalized(index%cols,inRow),
                        normalized(row,rows),width)
                }
            }
        }
    }
    /** Magnetic guides: return nearest center alignment only within 3%. */
    fun snap(value:Float,others:List<Float>,threshold:Float=.028f):Float{
        val candidates=others+0.5f
        val target=candidates.minByOrNull{kotlin.math.abs(it-value)}?:return value
        return if(kotlin.math.abs(value-target)<=threshold)target else value
    }
}
