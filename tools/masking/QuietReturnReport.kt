package com.heysig.masking

import com.heysig.noise.*
import java.io.File

/** Offline actual shared classifier/quiet corroboration/reducer. Never fits or plays audio. */
fun main(args:Array<String>) {
    val head=SpeechMaskingHead.decode(File(args[0]).readText())
    val bands=SeparationMaskingBands.decode(File(args[1]).readText())
    val labels=EnvironmentClassifier.requiredLabels.toList()
    val neural=File(args[3]).readLines().drop(1).associate { line ->
        val v=line.split('\t');v[0].toInt() to SpeechNoiseEstimate(SeparationStatus.valueOf(v[1]),v[2].toDoubleOrNull(),
            v[3].toDoubleOrNull(),v[4].toDoubleOrNull(),v[5].toDouble(),v[6].toDouble(),v[7].toDoubleOrNull())
    }
    val frames=if(args.size>5) File(args[5]).readLines().associate { line ->
        val v=line.split('\t');v[0].toInt() to v[1].split(';').map { frame ->
            val scores=frame.split(',').map { it.toDouble() };require(scores.size==labels.size)
            labels.zip(scores).toMap()
        }
    } else null
    File(args[4]).bufferedWriter().use { out ->
        out.write("index\tbefore\tquietCandidate\tseverity\treturnProposal\treason\n")
        File(args[2]).useLines { lines -> lines.forEach { line ->
            val columns=line.split('\t');val index=columns[0].toInt();val v=columns[1].split(',').map { it.toDouble() }
            require(v.size in head.featureCount+labels.size+2..head.featureCount+labels.size+3)
            val pooled=DoubleArray(2048) { v[it]/20 }
            val acoustic=v.subList(2048,head.featureCount).toDoubleArray()
            val average=labels.indices.associate { labels[it] to v[head.featureCount+it] }
            val events=frames?.getValue(index) ?: List(5) { average }
            val offset=head.featureCount+labels.size;val rms=v[offset];val peak=v[offset+1];val lowFraction=v.getOrNull(offset+2)
            val measurement=NoiseMeasurement(16000,48000,3,rms,rms,rms,peak,0.0,0.0,emptySet())
            val before=head.classifyFeatures(pooled,acoustic,events,measurement,lowFraction)
            val e=if(before.quietCandidate) QuietRoomReturn.corroborate(before,neural.getValue(index)) else bands.corroborate(before,neural.getValue(index))
            var state=MaskingState();var target:Int?=null
            for(i in 1..3) {
                val d=observeMasking(state,e,i*15000L,i*15000L+10,NoiseSettings(),MaskingChoices(1,3,6,5,true),
                    NoiseSafety(foreground=true,unlocked=true,audioFree=true),5)
                state=d.state
                if(d.proposed!=null) { target=d.proposed;break }
            }
            out.write("$index\t${before.severity}\t${before.quietCandidate}\t${e.severity}\t${target ?: "hold"}\t${e.reason}\n")
        } }
    }
}
