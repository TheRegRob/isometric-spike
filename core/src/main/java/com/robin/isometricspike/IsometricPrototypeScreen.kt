package com.robin.isometricspike

import com.badlogic.gdx.ApplicationAdapter
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.Animation
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.Vector3
import kotlin.math.atan2
import kotlin.math.roundToInt

/**
 * Le 8 direzioni standard di uno spritesheet isometrico.
 * L'ordine numerico (0-7) serve per la mappatura da angolo a direzione.
 */
enum class Direction(val folderSuffix: String) {
    SOUTH("south"),       // 0
    SOUTH_EAST("southeast"), // 1
    EAST("east"),         // 2
    NORTH_EAST("northeast"), // 3
    NORTH("north"),       // 4
    NORTH_WEST("northwest"), // 5
    WEST("west"),         // 6
    SOUTH_WEST("southwest")  // 7
}

/**
 * Carica un set di animazioni da spritesheet a griglia, organizzati in sottocartelle
 * per azione: assets/character/<actionFolder>/<filenamePrefix>_dir<N>.png
 * dove N va da 1 a 8 (le 8 direzioni, secondo il naming del pacchetto).
 */
class CharacterAnimations {
    private val animations = mutableMapOf<Pair<String, Direction>, Animation<TextureRegion>>()
    private val loadedTextures = mutableListOf<Texture>() // per dispose() pulito

    /**
     * Carica uno spritesheet a griglia e ne ricava un'animazione per una specifica azione+direzione.
     *
     * @param actionFolder sottocartella dell'azione, es. "walk" o "idle"
     * @param filenamePrefix es. "Boxer__WalkFoward" (senza "_dirN.png")
     * @param direction la direzione corrispondente a questo file
     * @param directionIndex il numero N nel nome file (_dirN), secondo il naming del pacchetto
     * @param cols numero di colonne della griglia
     * @param rows numero di righe della griglia
     * @param validFrameCount quanti frame della griglia sono effettivamente usati
     *                        (le celle in eccedenza, es. l'ultima vuota, vengono scartate)
     */
    fun loadDirection(
        action: String,
        actionFolder: String,
        filenamePrefix: String,
        direction: Direction,
        directionIndex: Int,
        cols: Int,
        rows: Int,
        validFrameCount: Int,
        frameDuration: Float = 0.08f
    ) {
        val path = "character/$actionFolder/${filenamePrefix}_dir$directionIndex.png"
        val texture = Texture(Gdx.files.internal(path))
        loadedTextures.add(texture)

        // TextureRegion.split() ritorna una matrice [riga][colonna] di sotto-regioni
        val frameWidth = texture.width / cols
        val frameHeight = texture.height / rows
        val grid = TextureRegion.split(texture, frameWidth, frameHeight)

        // Appiattiamo la griglia in una lista unica, riga per riga,
        // prendendo solo i primi validFrameCount frame (scartiamo celle vuote finali).
        val frames = com.badlogic.gdx.utils.Array<TextureRegion>()
        var count = 0
        outer@ for (row in grid) {
            for (region in row) {
                if (count >= validFrameCount) break@outer
                frames.add(region)
                count++
            }
        }

        animations[action to direction] = Animation(frameDuration, frames, Animation.PlayMode.LOOP)
    }

    /**
     * Ritorna il frame corrente per azione+direzione. Se l'azione richiesta non e'
     * (ancora) caricata, ripiega su "walk" nella stessa direzione, cosi' il personaggio
     * non sparisce a schermo mentre aggiungi gradualmente le altre animazioni.
     */
    fun getFrame(action: String, direction: Direction, stateTime: Float): TextureRegion? {
        animations[action to direction]?.let { return it.getKeyFrame(stateTime, true) }
        return animations["walk" to direction]?.getKeyFrame(stateTime, true)
    }

    fun dispose() {
        loadedTextures.forEach { it.dispose() }
    }
}

/**
 * Converte un vettore di movimento (dx, dy) nella direzione discreta piu' vicina
 * tra le 8 disponibili. Usa atan2 per l'angolo, poi arrotonda a step di 45 gradi.
 */
fun directionFromVector(dx: Float, dy: Float): Direction {
    if (dx == 0f && dy == 0f) return Direction.SOUTH // default a riposo
    val angleDeg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
    val normalized = (angleDeg + 360f) % 360f
    // Ogni direzione copre uno spicchio di 45 gradi, partendo da EST (0 gradi)
    val index = (normalized / 45f).roundToInt() % 8
    // Mappatura indice -> direzione seguendo il cerchio trigonometrico standard
    return when (index) {
        0 -> Direction.EAST
        1 -> Direction.NORTH_EAST
        2 -> Direction.NORTH
        3 -> Direction.NORTH_WEST
        4 -> Direction.WEST
        5 -> Direction.SOUTH_WEST
        6 -> Direction.SOUTH
        else -> Direction.SOUTH_EAST
    }
}

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

    // Calibrazione della mappatura dirN -> Direction per lo spritesheet "Boxer__WalkFoward".
    // Valore confermato funzionante: ROTATION_OFFSET = 1 (REVERSE_ROTATION non necessario).
    private val ROTATION_OFFSET = 1
    private val REVERSE_ROTATION = false

    private lateinit var shapeRenderer: ShapeRenderer
    private lateinit var spriteBatch: SpriteBatch
    private lateinit var camera: OrthographicCamera
    private lateinit var characterAnimations: CharacterAnimations

    // Oggetti "finti" (al posto di sprite) per dimostrare il depth sorting.
    // Ognuno ha una posizione nella griglia logica (gridX, gridY) e un colore.
    data class WorldObject(var gridX: Float, var gridY: Float, val color: Color, val size: Float)

    private val objects = mutableListOf(
        WorldObject(2f, 2f, Color.RED, 40f),
        WorldObject(3f, 3f, Color.BLUE, 40f),
        WorldObject(2f, 4f, Color.GREEN, 40f),
        WorldObject(5f, 1f, Color.YELLOW, 40f)
    )

    // Stato del player: posizione + direzione/azione corrente + tempo per l'animazione
    private var playerGridX = 4f
    private var playerGridY = 4f
    private var playerDirection = Direction.SOUTH
    private var playerAction = "idle" // "idle" o "walk", aggiornato in handleInput
    private var stateTime = 0f

    override fun create() {
        shapeRenderer = ShapeRenderer()
        spriteBatch = SpriteBatch()
        camera = OrthographicCamera()
        camera.setToOrtho(false, Gdx.graphics.width.toFloat(), Gdx.graphics.height.toFloat())

        characterAnimations = CharacterAnimations()
        // Ordine di riferimento (ottavo 0 = SOUTH), su cui applichiamo poi
        // ROTATION_OFFSET e REVERSE_ROTATION per i tentativi manuali.
        val baseOrder = listOf(
            Direction.SOUTH,       // ottavo 0
            Direction.SOUTH_WEST,  // ottavo 1
            Direction.WEST,        // ottavo 2
            Direction.NORTH_WEST,  // ottavo 3
            Direction.NORTH,       // ottavo 4
            Direction.NORTH_EAST,  // ottavo 5
            Direction.EAST,        // ottavo 6
            Direction.SOUTH_EAST   // ottavo 7
        )

        for (dirNumber in 1..8) {
            var eighth = (dirNumber - 1 + ROTATION_OFFSET) % 8
            if (eighth < 0) eighth += 8
            if (REVERSE_ROTATION) eighth = (8 - eighth) % 8
            val direction = baseOrder[eighth]

            characterAnimations.loadDirection(
                action = "walk",
                actionFolder = "walk",
                filenamePrefix = "Boxer__WalkFoward",
                direction = direction,
                directionIndex = dirNumber,
                cols = 5,
                rows = 5,
                validFrameCount = 24 // griglia 5x5 = 25 celle, l'ultima e' vuota
            )
        }

        for (dirNumber in 1..8) {
            var eighth = (dirNumber - 1 + ROTATION_OFFSET) % 8
            if (eighth < 0) eighth += 8
            if (REVERSE_ROTATION) eighth = (8 - eighth) % 8
            val direction = baseOrder[eighth]

            characterAnimations.loadDirection(
                action = "idle",
                actionFolder = "idle",
                filenamePrefix = "Boxer__idle",
                direction = direction,
                directionIndex = dirNumber,
                cols = 6,
                rows = 6,
                validFrameCount = 31 // griglia 6x6 = 36 celle, ultime 5 vuote
            )
        }
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
        val speed = 2f

        // Costruiamo il vettore di direzione grezzo (-1, 0, o 1 per asse)
        var dx = 0f
        var dy = 0f
        if (Gdx.input.isKeyPressed(Input.Keys.RIGHT)) dx += 1f
        if (Gdx.input.isKeyPressed(Input.Keys.LEFT)) dx -= 1f
        if (Gdx.input.isKeyPressed(Input.Keys.UP)) dy += 1f
        if (Gdx.input.isKeyPressed(Input.Keys.DOWN)) dy -= 1f

        // Normalizziamo: se ci muoviamo su entrambi gli assi (movimento diagonale),
        // il vettore (dx, dy) avrebbe lunghezza sqrt(2) invece di 1, rendendo
        // il movimento diagonale piu' veloce di quello sui singoli assi.
        val direction = Vector3(dx, dy, 0f)
        val isMoving = direction.len2() > 0f
        if (isMoving) {
            direction.nor()
            // Aggiorna la direzione visiva solo quando ci si muove davvero,
            // cosi' il personaggio resta rivolto nell'ultima direzione quando si ferma.
            //
            // IMPORTANTE: la direzione va calcolata sul vettore PROIETTATO A SCHERMO,
            // non su (dx, dy) della griglia grezza. In isometrico i due spazi sono
            // "ruotati" tra loro: un movimento diagonale sulla griglia (es. sx+giu)
            // puo' risultare in un movimento puramente verticale a schermo, e viceversa.
            // Usiamo la stessa trasformazione di gridToScreen, ignorando origin/traslazione
            // perche' qui ci interessa solo la DIREZIONE del vettore, non la posizione.
            val screenDx = (dx - dy) * (tileWidth / 2f)
            val screenDy = (dx + dy) * (tileHeight / 2f)
            playerDirection = directionFromVector(screenDx, screenDy)
        }
        playerAction = if (isMoving) "walk" else "idle"

        playerGridX += direction.x * speed * delta
        playerGridY += direction.y * speed * delta
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

    /**
     * Depth sorting unificato: mescoliamo oggetti-cerchio (placeholder) e player
     * (sprite vero) in un'unica lista ordinabile, cosi' il player si sovrappone
     * o viene coperto correttamente rispetto agli altri, esattamente come faranno
     * poi tutti gli sprite reali del gioco.
     */
    private sealed class Drawable(val gridX: Float, val gridY: Float) {
        class Circle(x: Float, y: Float, val color: Color, val size: Float) : Drawable(x, y)
        class Sprite(x: Float, y: Float, val region: TextureRegion) : Drawable(x, y)
    }

    private fun drawObjects(originX: Float, originY: Float) {
        val playerFrame = characterAnimations.getFrame(playerAction, playerDirection, stateTime)

        val drawables = mutableListOf<Drawable>()
        objects.forEach { drawables.add(Drawable.Circle(it.gridX, it.gridY, it.color, it.size)) }
        if (playerFrame != null) {
            drawables.add(Drawable.Sprite(playerGridX, playerGridY, playerFrame))
        }

        // *** DEPTH SORTING ***
        // Regola chiave dell'isometrico: piu' un oggetto e' "in basso" nella griglia
        // logica (gridX + gridY piccolo), piu' va disegnato per primo (dietro).
        val sorted = drawables.sortedBy { it.gridX + it.gridY }

        // I cerchi placeholder restano con ShapeRenderer, lo sprite del player con SpriteBatch.
        // Non possiamo mescolare le due chiamate begin/end, quindi le separiamo per gruppi
        // contigui mantenendo comunque l'ordine di profondita' complessivo.
        var i = 0
        while (i < sorted.size) {
            when (sorted[i]) {
                is Drawable.Circle -> {
                    shapeRenderer.begin(ShapeRenderer.ShapeType.Filled)
                    while (i < sorted.size && sorted[i] is Drawable.Circle) {
                        val c = sorted[i] as Drawable.Circle
                        val pos = gridToScreen(c.gridX, c.gridY)
                        shapeRenderer.color = c.color
                        shapeRenderer.circle(originX + pos.x, originY + pos.y + c.size / 2f, c.size / 2f)
                        i++
                    }
                    shapeRenderer.end()
                }
                is Drawable.Sprite -> {
                    spriteBatch.projectionMatrix = camera.combined
                    spriteBatch.begin()
                    while (i < sorted.size && sorted[i] is Drawable.Sprite) {
                        val s = sorted[i] as Drawable.Sprite
                        val pos = gridToScreen(s.gridX, s.gridY)
                        // Ancoriamo lo sprite per il centro-basso (piedi del personaggio),
                        // convenzione standard per sprite isometrici su una tile.
                        val width = s.region.regionWidth.toFloat()
                        val height = s.region.regionHeight.toFloat()
                        spriteBatch.draw(
                            s.region,
                            originX + pos.x - width / 2f,
                            originY + pos.y,
                            width,
                            height
                        )
                        i++
                    }
                    spriteBatch.end()
                }
            }
        }
    }

    override fun render() {
        handleInput(Gdx.graphics.deltaTime)
        stateTime += Gdx.graphics.deltaTime

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
        spriteBatch.dispose()
        characterAnimations.dispose()
    }
}
