package com.demomiru.tokeiv2.utils

import android.net.Uri
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.source.SingleSampleMediaSource
import com.demomiru.tokeiv2.subtitles.SubtitleConfig
import com.google.gson.Gson

data class ExtractedData(
    val videoUrl: String? = null,
    val subs: List<String> = listOf(),
    val source: String,
    val isSuper: Boolean = false,
)


class Extractor (private val origin: String){


    private val gson = Gson()
    private val extractorPriority = mapOf(
        "hi" to listOf(1,5,3,4),
        "en" to listOf(1,5,2,3),
        "" to listOf(1,5,2,3)
    )
    private val eList = extractorPriority[origin]
    var i = 0

    suspend fun loadExtractor(title: String, id: String, year: String = "1970", s: Int, ep: Int, isMovie: Boolean, next:Int = 1): ExtractedData{
        println(origin)
        return when(next){
            2 -> goMovieExtractor(title,s,ep,id,year,isMovie)
            1 -> superStreamExtractor(title,s,ep,id,year,isMovie)
            3 -> smashyExtractor(title,s,ep,id,year,isMovie)
            4 -> dudeFilmExtractor(title,s,ep,id,year,isMovie)
            5 -> vidSrcExtractor(title,s,ep,id,year,isMovie)
            else -> ExtractedData(source = "")
        }
    }

    suspend fun loadExtractorNext(title: String, id: String, s: Int, ep: Int, source: String?) : ExtractedData{
        return when(source){
            "superstream" -> superStreamExtractor(title,s,ep,id,"",false)
            "gomovies" -> goMovieExtractor(title,s,ep,id,"",false)
             "smashy" -> smashyExtractor(title,s,ep,id,"",false)
             "dudefilms" ->  dudeFilmExtractor(title,s,ep,id,"",false)
            "vidsrc"   -> vidSrcExtractor(title,s,ep,id,"",false)
            else-> ExtractedData(source = "")
        }
    }

    suspend fun loadSourceChange(title: String, id:String,s:Int, ep: Int,year: String,isMovie: Boolean, source: String? = null) : List<ExtractedData>{
        val listSources = mutableListOf<ExtractedData>()
        listSources.add(superStreamExtractor(title,s,ep,id,year,isMovie,true))
        listSources.add(vidSrcExtractor(title,s,ep,id,year,isMovie,true))
        listSources.add(goMovieExtractor(title,s,ep,id,year,isMovie,true))
        listSources.add(smashyExtractor(title,s,ep,id,year,isMovie,true))
        listSources.add(dudeFilmExtractor(title,s,ep,id,year,isMovie))

        listSources.removeIf{
            it.videoUrl.isNullOrBlank()
        }
        if(origin == "hi")
            listSources.removeIf{
                it.source == "gomovies"
            }
        if(source!=null)
            listSources.removeIf {
                it.source == source
            }
        println(listSources)
        return listSources.toList()
    }



    private suspend fun superStreamExtractor(title: String,s:Int, ep: Int, id: String, year: String, isMovie: Boolean,srcChange: Boolean = false) : ExtractedData{
        val superStream = SuperstreamUtils()
        var videoUrl: String? = null
        val subUrl : MutableList<String> = mutableListOf()
        try {
            val mainData = superStream.search(title)
            val item = mainData.data.list[0]
            val superId =
                if (item.title == title && item.year.toString() == year) item.id else if(!isMovie) item.id else throw Exception(
                    "No super stream found"
                )
            val movieLinks = superStream.loadLinks(isMovie, superId!!,s,ep)
            println(movieLinks)
            val urlMaps: MutableMap<String, String> = mutableMapOf()
            movieLinks.data?.list?.forEach {
                if (!it.path.isNullOrBlank()) {
                    urlMaps[it.quality!!] = it.path
                    if (it.quality == "720p") {
                        val subtitle = superStream.loadSubtile(isMovie, it.fid!!, superId, s,ep).data
                        subUrl.add(getSub2(subtitle))
                        return@forEach
                    }
                }
            }

            if(urlMaps.isNotEmpty())
                videoUrl = gson.toJson(urlMaps)
            if(videoUrl.isNullOrBlank()){

//                    isSuper = false
                throw Exception("No super stream found")
//                   return loadExtractor(title,id,year,s,ep,isMovie, eList!![++i])
            }
        }catch(e:Exception) {
            e.printStackTrace()
            return if(!srcChange)loadExtractor(title, id, year,s,ep,isMovie,eList!![++i]) else ExtractedData(source = "")
        }
        println("Superstream")
        return ExtractedData(videoUrl,subUrl,"superstream",true)
    }


    private fun getSub(subtitle: SuperstreamUtils.PrivateSubtitleData?): List<String>{
        val subUrl: MutableList<String> = mutableListOf()
        subtitle?.list?.forEach { subList->
            if(subList.language == "English"){
                subList.subtitles.forEach { sub->

                    if (subUrl.size == 3) {
                        return subUrl
                    }
                    if (sub.lang == "en" && !sub.file_path.isNullOrBlank()) {
                        subUrl.add(sub.file_path)
//                            println("${sub.language} : ${sub.file_path}")
                    }
                }
                return subUrl
            }
        }
        return listOf()
    }

    private fun getSub2(subtitle: SuperstreamUtils.PrivateSubtitleData?): String{
        val subUrl: MutableMap<String,String> = mutableMapOf()
        subtitle?.list?.forEach { subList ->
            if(subList.language == null) return@forEach
            var subsString = ""
            subList.subtitles.forEach { sub ->
                if (!sub.file_path.isNullOrBlank()) subsString += "${sub.file_path},"
            }
            subsString = subsString.substringBeforeLast(",")
            subUrl[subList.language] = subsString
        }
        return gson.toJson(subUrl)
    }

    private suspend fun vidSrcExtractor(title: String, s: Int,ep:Int, id: String,year:String,isMovie: Boolean,srcChange: Boolean = false): ExtractedData{
        val vidSrc = VidSrc()
        val videoUrl: String?
        val subUrl: ArrayList<String> = arrayListOf()
        try {
            val links =  vidSrc.getLink(id,isMovie,s,ep)
            val vidLink = links.first
            val subLink = links.second
            if(vidLink.isNullOrBlank()) throw Exception("No vidsrc found")
            else {
                if (!subLink.isNullOrBlank())subUrl.add(subLink)
                videoUrl = vidLink
            }
        }catch (e:Exception){
            e.printStackTrace()
            return if(!srcChange)  loadExtractor(title, id, year, s, ep, isMovie,eList!![++i]) else ExtractedData(source = "")
        }
        return ExtractedData(videoUrl,subUrl,"vidsrc",false)

    }

    private suspend fun goMovieExtractor(title: String,s: Int, ep: Int, id: String,year: String,isMovie: Boolean,srcChange: Boolean = false): ExtractedData{
        val goMovie = GoMovies()
        var videoUrl: String? = null
        val subUrl : ArrayList<String> = arrayListOf()

        try{
            val data = goMovie.search(s,ep,title,isMovie,year)
            val vidLink = data.first
            val subLinks = data.second
            if(vidLink.isNullOrBlank()){
//                return loadExtractor(title, id, year, s, ep, isMovie,eList!![++i])
                throw Exception("No go movies found")
            }
            else{
                if (!subLinks.isNullOrEmpty())subUrl.add(subLinks)
                videoUrl = vidLink
            }
        }catch (e:Exception){
            return if(!srcChange)   loadExtractor(title, id, year, s, ep, isMovie,eList!![++i]) else ExtractedData(source = "")
        }
        println("GoMovies")
        return ExtractedData(videoUrl,subUrl,"gomovies",false)
    }

    private suspend fun smashyExtractor(title: String,s: Int, ep: Int, id: String,year: String,isMovie: Boolean,srcChange: Boolean = false): ExtractedData{
        var videoUrl : String? = null
        val subUrl : ArrayList<String> = arrayListOf()
        val smashSrc = SmashyStream()
        try{
            val links = smashSrc.getLink(isMovie,id, s, ep,origin)
            val vidLink = links.first
            val subLink = links.second
            if(vidLink.isNullOrBlank()){
//                return if (origin == "hi")
//                    loadExtractor(title,id,year,s,ep,isMovie,eList!![++i])
//                else
//                    loadExtractor(title,id,year,s,ep,isMovie,0)
                throw Exception("No smashy stream found")
            }
            else{
                if (!subLink.isNullOrBlank())subUrl.add(subLink)
                videoUrl = vidLink
            }


        }catch (e: Exception){
            return if(!srcChange) {
                if (origin == "hi")
                    loadExtractor(title,id,year,s,ep,isMovie,eList!![++i])
                else
                    loadExtractor(title,id,year,s,ep,isMovie,0)
            }else ExtractedData(source = "")
        }
        println("Smashy")
        return ExtractedData(videoUrl,subUrl,"smashy",false)
    }

    //TODO
    private suspend fun dudeFilmExtractor(title: String, s: Int, ep: Int, id: String, year: String, movie: Boolean): ExtractedData {
        var videoUrl : String? = null
        try {
            videoUrl = if (movie) {
                val imdb = getMovieImdb(id)
                println(imdb)
                getMovieLink(imdb)
            } else {
                val imdb = getTvImdb(id)
                getTvLink(imdb, s-1, ep-1)
            }
        }  catch (e: Exception){
            return ExtractedData(source = "")
        }
        println("DudeFilms")
        return ExtractedData(videoUrl, listOf(),"dudefilms",false)
    }
}