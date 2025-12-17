package com.example.myapplication.scoring

import com.example.myapplication.data.model.DrowsinessFeatures

/**
 * Interface for drowsiness prediction models.
 * While we originally planned this to switch b/w 2 models (Heuristic and TFLite)
 * We ended up only implementing the heuristic one.
 */
interface DrowsinessModel {

    fun predict(features: DrowsinessFeatures): Float
}

