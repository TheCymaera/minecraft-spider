package com.heledron.spideranimation.utilities.inverse_kinematics

import kotlin.math.abs

private const val EPSILON = 0.000001

interface DampedLeastSquaresMetrics {
    val errorVector: DoubleArray
    val score: Double
    val isSolved: Boolean
}

class DampedLeastSquaresOptions(
    val iterations: Int = 24,
    val damping: Double = 0.1,
    val minDamping: Double = 0.02,
    val maxDamping: Double = 8.0,
    val dampingScale: Double = 2.5,
    val finiteDifference: Double = 0.3,
    val maxStep: Double = 1.0,
    val minStepScale: Double = 1.0 / 64,
)

interface LeastSquaresProblem<State, Parameter, Metrics : DampedLeastSquaresMetrics> {
    fun cloneState(state: State): State
    fun copyState(destination: State, source: State)
    fun evaluateState(state: State): Metrics
    fun listParameters(state: State): List<Parameter>

    /** Applies `delta` to `parameter`, returning the delta actually applied after clamping. */
    fun perturbParameter(state: State, parameter: Parameter, delta: Double): Double

    /** Returns the delta that perturbParameter would apply, i.e. `delta` clipped to the parameter's range. */
    fun clampDelta(state: State, parameter: Parameter, delta: Double): Double

    fun applyStep(state: State, parameters: List<Parameter>, step: DoubleArray, scale: Double)
}

fun <State, Parameter, Metrics : DampedLeastSquaresMetrics> solveDampedLeastSquares(
    state: State,
    problem: LeastSquaresProblem<State, Parameter, Metrics>,
    options: DampedLeastSquaresOptions,
) {
    var currentMetrics = problem.evaluateState(state)
    var bestState = problem.cloneState(state)
    var bestMetrics = currentMetrics
    var damping = options.damping.coerceIn(options.minDamping, options.maxDamping)

    for (iteration in 0 until options.iterations) {
        if (currentMetrics.isSolved) break

        val parameters = problem.listParameters(state)
        if (parameters.isEmpty()) break

        val jacobian = computeJacobian(problem, state, currentMetrics, parameters, options.finiteDifference)
        val step = solveDampedLeastSquaresStep(problem, state, parameters, jacobian, currentMetrics.errorVector, damping)
        if (step == null) {
            damping = minOf(options.maxDamping, damping * options.dampingScale)
            continue
        }

        limitStepMagnitude(step, options.maxStep)

        var accepted = false
        var stepScale = 1.0
        while (stepScale >= options.minStepScale) {
            val trialState = problem.cloneState(state)
            problem.applyStep(trialState, parameters, step, stepScale)

            val trialMetrics = problem.evaluateState(trialState)
            if (trialMetrics.score < currentMetrics.score - EPSILON) {
                problem.copyState(state, trialState)
                currentMetrics = trialMetrics
                accepted = true

                if (trialMetrics.score < bestMetrics.score - EPSILON) {
                    bestState = problem.cloneState(trialState)
                    bestMetrics = trialMetrics
                }

                damping = maxOf(options.minDamping, damping / options.dampingScale)
                break
            }

            stepScale *= 0.5
        }

        if (!accepted) {
            damping = minOf(options.maxDamping, damping * options.dampingScale)
        }
    }

    problem.copyState(state, bestState)
}

private fun <State, Parameter, Metrics : DampedLeastSquaresMetrics> computeJacobian(
    problem: LeastSquaresProblem<State, Parameter, Metrics>,
    state: State,
    baseMetrics: Metrics,
    parameters: List<Parameter>,
    finiteDifference: Double,
): Array<DoubleArray> {
    val rows = baseMetrics.errorVector.size
    val jacobian = Array(rows) { DoubleArray(parameters.size) }

    for (column in parameters.indices) {
        val forwardState = problem.cloneState(state)
        val backwardState = problem.cloneState(state)
        val parameter = parameters[column]
        val forwardDelta = problem.perturbParameter(forwardState, parameter, finiteDifference)
        val backwardDelta = problem.perturbParameter(backwardState, parameter, -finiteDifference)

        if (abs(forwardDelta) > EPSILON && abs(backwardDelta) > EPSILON) {
            val forwardMetrics = problem.evaluateState(forwardState)
            val backwardMetrics = problem.evaluateState(backwardState)

            for (row in 0 until rows) {
                jacobian[row][column] =
                    (forwardMetrics.errorVector[row] - backwardMetrics.errorVector[row]) / (forwardDelta - backwardDelta)
            }
            continue
        }

        // One side was clamped to zero: fall back to a one-sided difference.
        var sampledMetrics: Metrics? = null
        var denominator = 0.0

        if (abs(forwardDelta) > EPSILON) {
            sampledMetrics = problem.evaluateState(forwardState)
            denominator = forwardDelta
        } else if (abs(backwardDelta) > EPSILON) {
            sampledMetrics = problem.evaluateState(backwardState)
            denominator = backwardDelta
        }

        if (sampledMetrics == null || abs(denominator) <= EPSILON) continue

        for (row in 0 until rows) {
            jacobian[row][column] = (sampledMetrics.errorVector[row] - baseMetrics.errorVector[row]) / denominator
        }
    }

    return jacobian
}

/** Solves `(JᵀJ + λ²I) · step = -Jᵀ · error`, returning null if the system is singular. */
private fun <State, Parameter, Metrics : DampedLeastSquaresMetrics> solveDampedLeastSquaresStep(
    problem: LeastSquaresProblem<State, Parameter, Metrics>,
    state: State,
    parameters: List<Parameter>,
    jacobian: Array<DoubleArray>,
    errorVector: DoubleArray,
    damping: Double,
): DoubleArray? {
    val columnCount = parameters.size
    if (columnCount == 0) return null

    val active = BooleanArray(columnCount) { true }

    while (true) {
        val step = solveRestrictedStep(jacobian, errorVector, damping, active) ?: return null

        var pinned = false

        for (index in 0 until columnCount) {
            if (!active[index]) continue

            val delta = step[index]
            if (abs(delta) <= EPSILON) continue

            val applied = problem.clampDelta(state, parameters[index], delta)
            step[index] = applied

            if (abs(applied) <= EPSILON) {
                active[index] = false
                pinned = true
            }
        }

        if (!pinned) return step
        if (active.none { it }) return null
    }
}

private fun solveRestrictedStep(
    jacobian: Array<DoubleArray>,
    errorVector: DoubleArray,
    damping: Double,
    active: BooleanArray,
): DoubleArray? {
    val columns = active.indices.filter { active[it] }
    if (columns.isEmpty()) return null

    val size = columns.size
    val normalMatrix = Array(size) { DoubleArray(size) }
    val rhs = DoubleArray(size)
    val dampingSquared = damping * damping

    for (row in jacobian.indices) {
        for (column in 0 until size) {
            val left = jacobian[row][columns[column]]
            rhs[column] -= left * errorVector[row]

            for (innerColumn in 0 until size) {
                normalMatrix[column][innerColumn] += left * jacobian[row][columns[innerColumn]]
            }
        }
    }

    for (index in 0 until size) {
        normalMatrix[index][index] += dampingSquared
    }

    val solution = solveLinearSystem(normalMatrix, rhs) ?: return null

    val step = DoubleArray(active.size)
    for (index in 0 until size) {
        if (!solution[index].isFinite()) return null
        step[columns[index]] = solution[index]
    }

    return step
}

/** Gauss-Jordan elimination with partial pivoting. */
private fun solveLinearSystem(matrix: Array<DoubleArray>, rhs: DoubleArray): DoubleArray? {
    val size = rhs.size
    val augmented = Array(size) { row -> DoubleArray(size + 1) { column -> if (column == size) rhs[row] else matrix[row][column] } }

    for (pivot in 0 until size) {
        var pivotRow = pivot
        for (row in pivot + 1 until size) {
            if (abs(augmented[row][pivot]) > abs(augmented[pivotRow][pivot])) {
                pivotRow = row
            }
        }

        if (abs(augmented[pivotRow][pivot]) <= EPSILON) return null

        if (pivotRow != pivot) {
            val swap = augmented[pivot]
            augmented[pivot] = augmented[pivotRow]
            augmented[pivotRow] = swap
        }

        val pivotValues = augmented[pivot]
        val pivotValue = pivotValues[pivot]
        for (column in pivot..size) {
            pivotValues[column] /= pivotValue
        }

        for (row in 0 until size) {
            if (row == pivot) continue

            val currentRow = augmented[row]
            val factor = currentRow[pivot]
            if (abs(factor) <= EPSILON) continue

            for (column in pivot..size) {
                currentRow[column] -= factor * pivotValues[column]
            }
        }
    }

    return DoubleArray(size) { augmented[it][size] }
}

private fun limitStepMagnitude(step: DoubleArray, maxStep: Double) {
    var maxMagnitude = 0.0
    for (value in step) maxMagnitude = maxOf(maxMagnitude, abs(value))

    if (maxMagnitude <= maxStep || maxMagnitude <= EPSILON) return

    val scale = maxStep / maxMagnitude
    for (index in step.indices) step[index] *= scale
}
