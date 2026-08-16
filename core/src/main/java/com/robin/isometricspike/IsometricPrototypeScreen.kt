package com.robin.isometricspike

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector3

/**
 * Prototipo minimo per validare i due pilastri tecnici di un gioco isometrico:
 *  1) conversione di coordinate world (griglia logica) <-> screen (dove disegnare)
 *  2) depth sorting (ordine di disegno corretto in base alla posizione)
 *
 * Nessun asset grafico: tile e oggetti sono forme geometriche colorate,
 * cosi' puoi concentrarti sulla logica prima di introdurre sprite veri.
 */
class IsometricPrototypeScreen : ApplicationAdapter() {

    // Dimensioni di una tile isometrica in pixel (proporzione 2:1, la piu' comune)
    private val tileWidth = 128f
    private val tileHeight = 64f

    // Dimensioni della griglia logica (in tile, non in pixel)
    private val gridCols = 8
    private val gridRows = 8

    private lateinit var shapeRenderer: ShapeRenderer
    private lateinit var camera: OrthographicCamera

    // Oggetti "finti" (al posto di sprite) per dimostrare il depth sorting.
    // Ognuno ha una posizione nella griglia logica (gridX, gridY) e un colore.
    data class WorldObject(var gridX: Float, var gridY: Float, val color: Color, val size: Float)

    private val objects = mutableListOf(
        WorldObject(2f, 2f, Color.RED, 40f),
        WorldObject(3f, 3f, Color.BLUE, 40f),
        WorldObject(2f, 4f, Color.GREEN, 40f),
        WorldObject(5f, 1f, Color.YELLOW, 40f)
    )

    // L'oggetto controllabile con le frecce, per testare il sorting dal vivo
    private val player = WorldObject(4f, 4f, Color.WHITE, 30f)

    override fun create() {
        shapeRenderer = ShapeRenderer()
        camera = OrthographicCamera()
        camera.setToOrtho(false, Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat())
    }

    /**
     * Conversione da coordinate logiche (griglia cartesiana, gridX/gridY)
     * a coordinate schermo (dove disegnare effettivamente in pixel).
     * Questa e' LA formula chiave dell'isometrico "a rombi" (2:1 diamond).
     */
    private fun gridToScreen(gridX: Float, gridY: Float): Vector3 {
        val screenX = (gridX - gridY) * (tileWidth / 2f)
        val screenY = (gridX + gridY) * (tileHeight / 2f)
        return Vector3(screenX, screenY, 0f)
    }

    /**
     * Conversione inversa: da un punto schermo (es. click del mouse)
     * a coordinate di griglia. Utile per selezione tile / pathfinding.
     */
    private fun screenToGrid(screenX: Float, screenY: Float): Vector3 {
        val gridX = (screenX / (tileWidth / 2f) + screenY / (tileHeight / 2f)) / 2f
        val gridY = (screenY / (tileHeight / 2f) - screenX / (tileWidth / 2f)) / 2f
        return Vector3(gridX, gridY, 0f)
    }

    private fun handleInput(delta: Float) {
        val speed = 2f * delta
        if (Gdx.input.isKeyPressed(Input.Keys.RIGHT)) player.gridX += speed
        if (Gdx.input.isKeyPressed(Input.Keys.LEFT)) player.gridX -= speed
        if (Gdx.input.isKeyPressed(Input.Keys.UP)) player.gridY += speed
        if (Gdx.input.isKeyPressed(Input.Keys.DOWN)) player.gridY -= speed
    }

    private fun drawGrid(originX: Float, originY: Float) {
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line)
        shapeRenderer.color = Color.DARK_GRAY
        for (x in 0..gridCols) {
            for (y in 0..gridRows) {
                val pos = gridToScreen(x.toFloat(), y.toFloat())
                val sx = originX + pos.x
                val sy = originY + pos.y
                // Disegna il contorno a rombo di ogni singola tile
                shapeRenderer.line(sx, sy, sx + tileWidth / 2f, sy + tileHeight / 2f)
                shapeRenderer.line(sx, sy, sx - tileWidth / 2f, sy + tileHeight / 2f)
            }
        }
        shapeRenderer.end()
    }

    private fun drawObjects(originX: Float, originY: Float) {
        // *** DEPTH SORTING ***
        // Regola chiave dell'isometrico: piu' un oggetto e' "in basso" nella griglia
        // logica (gridX + gridY piccolo), piu' va disegnato per primo (dietro).
        // Ordiniamo per (gridX + gridY) crescente prima di disegnare.
        val allObjects = objects + player
        val sorted = allObjects.sortedBy { it.gridX + it.gridY }

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
        for (obj in sorted) {
            val pos = gridToScreen(obj.gridX, obj.gridY)
            shapeRenderer.color = obj.color
            // Disegniamo un semplice cerchio come "segnaposto" per uno sprite
            shapeRenderer.circle(originX + pos.x, originY + pos.y + obj.size / 2f, obj.size / 2f)
        }
        shapeRenderer.end()
    }

    override fun render() {
        handleInput(Gdx.graphics.deltaTime)

        Gdx.gl.glClearColor(0.15f, 0.15f, 0.18f, 1f)
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT)

        camera.update()
        shapeRenderer.projectionMatrix = camera.combined

        // Origine di disegno: centro-alto dello schermo, cosi' la griglia
        // si sviluppa a rombo verso il basso invece di uscire dai bordi.
        val originX = Gdx.graphics.width / 2f
        val originY = Gdx.graphics.height - 100f - (gridRows * tileHeight / 2f)

        drawGrid(originX, originY)
        drawObjects(originX, originY)
    }

    override fun dispose() {
        shapeRenderer.dispose()
    }
}
