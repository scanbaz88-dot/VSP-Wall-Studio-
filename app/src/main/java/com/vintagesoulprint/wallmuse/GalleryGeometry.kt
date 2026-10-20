package com.vintagesoulprint.wallmuse

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Geometry-only group and gallery layout engine; no Android dependency.
 * Normalized coordinates are relative to the wall/photo rectangle.
 * Any physical dimensions are estimates until the wall is calibrated.
 */
object GalleryGeometry {
    data class Item(val id:Long,val cx:Float,val cy:Float,val width:Float,val ratio:Float) {
        val height get()=width*ratio.coerceIn(.25f,5f)
    }
    enum class Align{ LEFT,RIGHT,CENTER_X,TOP,BOTTOM,CENTER_Y,SPACE_X,SPACE_Y }
    fun align(items:List<Item>,mode:Align):List<Item>{
        if(items.size<2)return items
        val minX=items.minOf{(it.cx-it.width*.5f).toDouble()}.toFloat()
        val maxX=items.maxOf{(it.cx+it.width*.5f).toDouble()}.toFloat()
        val minY=items.minOf{(it.cy-it.height*.5f).toDouble()}.toFloat()
        val maxY=items.maxOf{(it.cy+it.height*.5f).toDouble()}.toFloat()
        return when(mode){
            Align.LEFT->items.map{it.copy(cx=(minX+it.width*.5f).coerceIn(.02f,.98f))}
            Align.RIGHT->items.map{it.copy(cx=(maxX-it.width*.5f).coerceIn(.02f,.98f))}
            Align.CENTER_X->items.map{it.copy(cx=(minX+maxX)*.5f)}
            Align.TOP->items.map{it.copy(cy=(minY+it.height*.5f).coerceIn(.02f,.98f))}
            Align.BOTTOM->items.map{it.copy(cy=(maxY-it.height*.5f).coerceIn(.02f,.98f))}
            Align.CENTER_Y->items.map{it.copy(cy=(minY+maxY)*.5f)}
            Align.SPACE_X->{
                val sorted=items.sortedBy{it.cx}
                val totalW=sorted.sumOf{it.width.toDouble()}.toFloat()
                val outerLeft=sorted.first().cx-sorted.first().width*.5f
                val outerRight=sorted.last().cx+sorted.last().width*.5f
                val gap=(outerRight-outerLeft-totalW)/(items.size-1)
                var cursor=outerLeft
                val byId=sorted.associate{ item->
                    val center=cursor+item.width*.5f
                    cursor+=item.width+gap
                    item.id to item.copy(cx=center.coerceIn(.02f,.98f))
                }
                items.map{byId[it.id]!!}
            }
            Align.SPACE_Y->{
                val sorted=items.sortedBy{it.cy}
                val totalH=sorted.sumOf{it.height.toDouble()}.toFloat()
                val outerTop=sorted.first().cy-sorted.first().height*.5f
                val outerBottom=sorted.last().cy+sorted.last().height*.5f
                val gap=(outerBottom-outerTop-totalH)/(items.size-1)
                var cursor=outerTop
                val byId=sorted.associate{item->
                    val center=cursor+item.height*.5f
                    cursor+=item.height+gap
                    item.id to item.copy(cy=center.coerceIn(.02f,.98f))
                }
                items.map{byId[it.id]!!}
            }
        }
    }
    /** Ratio-safe template: never distort an artwork to fit the slot. */
    fun layout(ratios:List<Float>,style:GalleryPlanner.Layout,gap:Float=.028f):List<GalleryPlanner.Placement>{
        val n=ratios.size.coerceAtMost(12)
        if(n==0)return emptyList()
        if(n==1)return listOf(GalleryPlanner.Placement(.5f,.48f,.34f))
        val actual=ratios.take(n).map{it.coerceIn(.25f,5f)}
        val spacing=gap.coerceIn(.006f,.07f)
        val usableW=.84f
        val usableH=.79f
        val cols=when(style){
            GalleryPlanner.Layout.ROW->min(5,n)
            GalleryPlanner.Layout.COLUMN->1
            GalleryPlanner.Layout.GRID->ceil(sqrt(n.toDouble())).toInt()
            GalleryPlanner.Layout.SALON->if(n>=7)3 else 2
            GalleryPlanner.Layout.PYRAMID->if(n>=8)4 else 3
        }
        val rows=ceil(n.toDouble()/cols).toInt()
        val cellW=(usableW-(cols-1)*spacing)/cols
        val cellH=(usableH-(rows-1)*spacing)/rows
        val centers=mutableListOf<GalleryPlanner.Placement>()
        for(i in 0 until n){
            val r=i/cols
            val c=i%cols
            val itemsThisRow=min(cols,n-r*cols)
            val offset=(cols-itemsThisRow)*(.5f*cellW+.5f*spacing)
            val x=.08f+offset+c*(cellW+spacing)+cellW*.5f
            val y=.105f+r*(cellH+spacing)+cellH*.5f
            val w=min(cellW,cellH/actual[i]).coerceAtLeast(.035f)
            centers+=GalleryPlanner.Placement(x.coerceIn(.02f,.98f),y.coerceIn(.02f,.98f),w)
        }
        return centers
    }
    /** Uniform resizing around group centroid, preserving internal spacing. */
    fun scale(items:List<Item>,factor:Float):List<Item>{
        if(items.isEmpty())return items
        val cx=items.map{it.cx.toDouble()}.average().toFloat()
        val cy=items.map{it.cy.toDouble()}.average().toFloat()
        val f=factor.coerceIn(.5f,1.5f)
        return items.map{
            it.copy(
                cx=(cx+(it.cx-cx)*f).coerceIn(.03f,.97f),
                cy=(cy+(it.cy-cy)*f).coerceIn(.03f,.97f),
                width=(it.width*f).coerceIn(.035f,.88f)
            )
        }
    }
}
