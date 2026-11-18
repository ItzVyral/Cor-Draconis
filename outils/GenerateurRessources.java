import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import javax.imageio.ImageIO;

/**
 * Générateur des ressources visuelles de Cor Draconis.
 *
 * Lancement (depuis la racine du projet) :   java outils/GenerateurRessources.java
 *
 * Il (re)crée :
 *  - les modèles GeckoLib des créatures (.geo.json, ouvrables et modifiables dans Blockbench),
 *    avec leur texture et leur "glowmask" (pixels lumineux) peintes automatiquement ;
 *  - les icônes 16x16 des objets, les textures de la Forge runique et son interface.
 *
 * ATTENTION : relancer l'outil ÉCRASE ces fichiers. Une fois que vous retouchez un modèle
 * ou une texture à la main (Blockbench, Aseprite...), ne relancez plus l'outil, ou
 * commentez la ligne correspondante dans main().
 *
 * NOTION JAVA — ce fichier n'appartient pas au mod : c'est un petit programme autonome.
 * Java 11+ sait exécuter directement un fichier .java sans étape de compilation séparée.
 */
public class GenerateurRessources {

	private static final Path ASSETS = Path.of("src/main/resources/assets/cordraconis");

	public static void main(final String[] args) throws IOException {
		Locale.setDefault(Locale.ROOT); // points décimaux "1.5" et non "1,5" dans les JSON

		genererCendreux();
		genererPremierGardien();

		genererEpees();
		genererRunes();
		genererObjets();
		genererOeufs();
		genererBlocForge();
		genererInterfaceForge();

		System.out.println("Ressources générées dans " + ASSETS.toAbsolutePath());
	}

	// =================================================================================
	//  MODÈLES GECKOLIB
	// =================================================================================

	/** Matières : décident de la peinture de chaque cube. */
	enum Matiere { CENDRE, BRAISE, ARMURE, OR, TISSU, LAME, CUIR }

	/** Un cube Blockbench (coordonnées en pixels, 16 px = 1 bloc). */
	static final class Cube {
		final double ox, oy, oz;
		final int sx, sy, sz;
		final Matiere matiere;
		final double[] rotation; // peut être null
		final double[] pivot;    // peut être null
		int u, v;

		Cube(double ox, double oy, double oz, int sx, int sy, int sz, Matiere matiere, double[] pivot, double[] rotation) {
			this.ox = ox; this.oy = oy; this.oz = oz;
			this.sx = sx; this.sy = sy; this.sz = sz;
			this.matiere = matiere;
			this.pivot = pivot;
			this.rotation = rotation;
		}

		int largeurUv() { return 2 * (sx + sz); }
		int hauteurUv() { return sz + sy; }
	}

	/** Un os (bone) : groupe de cubes qui bouge ensemble dans les animations. */
	static final class Os {
		final String nom, parent;
		final double[] pivot;
		final List<Cube> cubes = new ArrayList<>();

		Os(String nom, String parent, double... pivot) {
			this.nom = nom; this.parent = parent; this.pivot = pivot;
		}

		Os cube(double ox, double oy, double oz, int sx, int sy, int sz, Matiere m) {
			cubes.add(new Cube(ox, oy, oz, sx, sy, sz, m, null, null));
			return this;
		}

		Os cubeTourne(double ox, double oy, double oz, int sx, int sy, int sz, Matiere m, double[] pivot, double[] rotation) {
			cubes.add(new Cube(ox, oy, oz, sx, sy, sz, m, pivot, rotation));
			return this;
		}
	}

	private static void genererCendreux() throws IOException {
		List<Os> os = new ArrayList<>();
		os.add(new Os("racine", null, 0, 0, 0));
		os.add(new Os("corps", "racine", 0, 14, 0)
			.cube(-4.5, 14, -2.5, 9, 11, 5, Matiere.CENDRE)
			.cubeTourne(-7, 23, -2, 3, 2, 4, Matiere.CENDRE, new double[] {-5.5, 24, 0}, new double[] {0, 0, 20}));
		os.add(new Os("coeur", "corps", 0, 19.5, -2.5)
			.cube(-1.5, 18, -3, 3, 3, 1, Matiere.BRAISE));
		os.add(new Os("tete", "corps", 0, 25, -1)
			.cube(-4, 25, -4.5, 8, 8, 8, Matiere.CENDRE)
			.cubeTourne(-3, 32, -1, 1, 3, 1, Matiere.CENDRE, new double[] {-2.5, 33, -0.5}, new double[] {0, 0, 15})
			.cubeTourne(1.5, 32, 0.5, 1, 2, 1, Matiere.CENDRE, new double[] {2, 33, 1}, new double[] {-10, 0, -20}));
		os.add(new Os("yeux", "tete", 0, 28.5, -4.5)
			.cube(-3, 28, -5, 2, 1, 1, Matiere.BRAISE)
			.cube(1, 28, -5, 2, 1, 1, Matiere.BRAISE));
		os.add(new Os("bras_droit", "corps", -5.5, 24, 0)
			.cube(-8, 13, -2, 3, 12, 4, Matiere.CENDRE)
			.cube(-8.5, 10, -2.5, 4, 4, 5, Matiere.CENDRE));
		os.add(new Os("bras_gauche", "corps", 5.5, 24, 0)
			.cube(5, 13, -2, 3, 12, 4, Matiere.CENDRE)
			.cube(4.5, 10, -2.5, 4, 4, 5, Matiere.CENDRE));
		os.add(new Os("jambe_droite", "racine", -2.2, 14, 0)
			.cube(-4.2, 0, -2, 4, 14, 4, Matiere.CENDRE));
		os.add(new Os("jambe_gauche", "racine", 2.2, 14, 0)
			.cube(0.2, 0, -2, 4, 14, 4, Matiere.CENDRE));

		ecrireModele("cendreux", os, 64, 64, 2.0, 3.0, 1337L);
	}

	private static void genererPremierGardien() throws IOException {
		List<Os> os = new ArrayList<>();
		os.add(new Os("racine", null, 0, 0, 0));
		os.add(new Os("bassin", "racine", 0, 27, 0)
			.cube(-6.5, 24, -3.5, 13, 6, 7, Matiere.ARMURE)
			.cube(-7.5, 21, -4.5, 15, 4, 9, Matiere.TISSU));
		os.add(new Os("torse", "bassin", 0, 30, 0)
			.cube(-7, 30, -4, 14, 16, 8, Matiere.ARMURE)
			.cube(-5, 32, -5, 10, 11, 1, Matiere.OR));
		os.add(new Os("coeur", "torse", 0, 39, -5)
			.cube(-2, 37, -6, 4, 4, 1, Matiere.BRAISE));
		os.add(new Os("cape", "torse", 0, 45, 4)
			.cube(-6, 16, 4, 12, 29, 1, Matiere.TISSU));
		os.add(new Os("tete", "torse", 0, 46, 0)
			.cube(-4.5, 46, -4.5, 9, 10, 9, Matiere.ARMURE)
			.cube(-1, 55, -4, 2, 3, 8, Matiere.OR));
		os.add(new Os("visiere", "tete", 0, 50.5, -4.5)
			.cube(-3.5, 50, -5, 7, 1, 1, Matiere.BRAISE));
		os.add(new Os("bras_droit", "torse", -9, 43, 0)
			.cube(-12.5, 39, -5, 6, 6, 10, Matiere.OR)
			.cube(-11.5, 24, -3, 5, 17, 6, Matiere.ARMURE)
			.cube(-12, 20, -3.5, 6, 5, 7, Matiere.CUIR));
		os.add(new Os("epee", "bras_droit", -9, 22, 0)
			.cube(-10, 18, -1, 2, 6, 2, Matiere.CUIR)
			.cube(-11, 17, -4, 4, 1, 8, Matiere.OR)
			.cube(-9.5, 1, -1.5, 1, 16, 3, Matiere.LAME));
		os.add(new Os("bras_gauche", "torse", 9, 43, 0)
			.cube(6.5, 39, -5, 6, 6, 10, Matiere.OR)
			.cube(6.5, 24, -3, 5, 17, 6, Matiere.ARMURE)
			.cube(6, 20, -3.5, 6, 5, 7, Matiere.CUIR));
		os.add(new Os("jambe_droite", "bassin", -3.5, 25, 0)
			.cube(-6, 4, -3, 5, 21, 6, Matiere.ARMURE)
			.cube(-6.5, 0, -4, 6, 5, 8, Matiere.CUIR));
		os.add(new Os("jambe_gauche", "bassin", 3.5, 25, 0)
			.cube(1, 4, -3, 5, 21, 6, Matiere.ARMURE)
			.cube(0.5, 0, -4, 6, 5, 8, Matiere.CUIR));

		ecrireModele("premier_gardien", os, 128, 128, 3.0, 4.5, 777L);
	}

	/** Place les cubes dans la texture, peint texture + glowmask, puis écrit le .geo.json. */
	private static void ecrireModele(String nom, List<Os> os, int largeurTex, int hauteurTex, double largeurVisible, double hauteurVisible, long graine) throws IOException {
		// 1. Rangement des zones UV ("box UV" de Blockbench) par rangées.
		List<Cube> tous = new ArrayList<>();
		for (Os o : os) tous.addAll(o.cubes);
		List<Cube> tries = new ArrayList<>(tous);
		tries.sort(Comparator.comparingInt(Cube::hauteurUv).reversed());
		int x = 0, y = 0, hauteurRangee = 0;
		for (Cube c : tries) {
			if (x + c.largeurUv() > largeurTex) {
				x = 0;
				y += hauteurRangee;
				hauteurRangee = 0;
			}
			c.u = x;
			c.v = y;
			x += c.largeurUv();
			hauteurRangee = Math.max(hauteurRangee, c.hauteurUv());
		}
		if (y + hauteurRangee > hauteurTex) {
			throw new IllegalStateException("Texture trop petite pour " + nom);
		}

		// 2. Peinture.
		BufferedImage texture = new BufferedImage(largeurTex, hauteurTex, BufferedImage.TYPE_INT_ARGB);
		BufferedImage lueur = new BufferedImage(largeurTex, hauteurTex, BufferedImage.TYPE_INT_ARGB);
		Random alea = new Random(graine);
		for (Cube c : tous) {
			peindreCube(texture, lueur, c, alea);
		}
		ecrireImage(texture, "textures/entity/" + nom + ".png");
		ecrireImage(lueur, "textures/entity/" + nom + "_glowmask.png");

		// 3. Fichier .geo.json (format Bedrock 1.12.0, lu par GeckoLib et Blockbench).
		StringBuilder json = new StringBuilder();
		json.append("{\n\t\"format_version\": \"1.12.0\",\n\t\"minecraft:geometry\": [\n\t\t{\n");
		json.append("\t\t\t\"description\": {\n");
		json.append("\t\t\t\t\"identifier\": \"geometry.").append(nom).append("\",\n");
		json.append("\t\t\t\t\"texture_width\": ").append(largeurTex).append(",\n");
		json.append("\t\t\t\t\"texture_height\": ").append(hauteurTex).append(",\n");
		json.append("\t\t\t\t\"visible_bounds_width\": ").append(nb(largeurVisible)).append(",\n");
		json.append("\t\t\t\t\"visible_bounds_height\": ").append(nb(hauteurVisible)).append(",\n");
		json.append("\t\t\t\t\"visible_bounds_offset\": [0, ").append(nb(hauteurVisible / 2)).append(", 0]\n");
		json.append("\t\t\t},\n\t\t\t\"bones\": [\n");
		for (int i = 0; i < os.size(); i++) {
			Os o = os.get(i);
			json.append("\t\t\t\t{\n\t\t\t\t\t\"name\": \"").append(o.nom).append("\",\n");
			if (o.parent != null) {
				json.append("\t\t\t\t\t\"parent\": \"").append(o.parent).append("\",\n");
			}
			json.append("\t\t\t\t\t\"pivot\": ").append(vec(o.pivot));
			if (!o.cubes.isEmpty()) {
				json.append(",\n\t\t\t\t\t\"cubes\": [\n");
				for (int j = 0; j < o.cubes.size(); j++) {
					Cube c = o.cubes.get(j);
					json.append("\t\t\t\t\t\t{\"origin\": ").append(vec(c.ox, c.oy, c.oz));
					json.append(", \"size\": ").append(vec(c.sx, c.sy, c.sz));
					if (c.rotation != null) {
						json.append(", \"pivot\": ").append(vec(c.pivot));
						json.append(", \"rotation\": ").append(vec(c.rotation));
					}
					json.append(", \"uv\": [").append(c.u).append(", ").append(c.v).append("]}");
					json.append(j < o.cubes.size() - 1 ? ",\n" : "\n");
				}
				json.append("\t\t\t\t\t]");
			}
			json.append("\n\t\t\t\t}").append(i < os.size() - 1 ? ",\n" : "\n");
		}
		json.append("\t\t\t]\n\t\t}\n\t]\n}\n");
		ecrireTexte(json.toString(), "geckolib/models/entity/" + nom + ".geo.json");
	}

	/**
	 * Peint les 6 faces d'un cube dans sa zone "box UV" :
	 *   [  ][haut ][bas ][  ]
	 *   [est][avant][ouest][arrière]
	 */
	private static void peindreCube(BufferedImage tex, BufferedImage lueur, Cube c, Random alea) {
		int u = c.u, v = c.v, w = c.sx, h = c.sy, d = c.sz;
		peindreFace(tex, lueur, u + d, v, w, d, c.matiere, alea, Face.DESSUS);
		peindreFace(tex, lueur, u + d + w, v, w, d, c.matiere, alea, Face.DESSOUS);
		peindreFace(tex, lueur, u, v + d, d, h, c.matiere, alea, Face.COTE);
		peindreFace(tex, lueur, u + d, v + d, w, h, c.matiere, alea, Face.AVANT);
		peindreFace(tex, lueur, u + d + w, v + d, d, h, c.matiere, alea, Face.COTE);
		peindreFace(tex, lueur, u + 2 * d + w, v + d, w, h, c.matiere, alea, Face.ARRIERE);
	}

	enum Face { DESSUS, DESSOUS, COTE, AVANT, ARRIERE }

	private static void peindreFace(BufferedImage tex, BufferedImage lueur, int x0, int y0, int w, int h, Matiere m, Random alea, Face face) {
		if (w <= 0 || h <= 0) return;
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int couleur = couleurDeBase(m, alea, x, y, w, h, face);
				// Ombrage : bords légèrement plus sombres, dessous plus sombre, dessus plus clair.
				boolean bord = x == 0 || y == 0 || x == w - 1 || y == h - 1;
				float lumiere = bord ? 0.82F : 1.0F;
				if (face == Face.DESSOUS) lumiere *= 0.7F;
				if (face == Face.DESSUS) lumiere *= 1.1F;
				if (face == Face.COTE) lumiere *= 0.9F;
				if (m == Matiere.BRAISE) lumiere = 1.0F;
				poser(tex, x0 + x, y0 + y, eclaircir(couleur, lumiere));
				if (m == Matiere.BRAISE) {
					poser(lueur, x0 + x, y0 + y, couleur);
				}
			}
		}

		// Détails propres à chaque matière.
		switch (m) {
			case CENDRE -> fissures(tex, lueur, x0, y0, w, h, alea, Math.max(1, (w * h) / 40), 0xFF2A2522, true);
			case ARMURE -> {
				fissures(tex, lueur, x0, y0, w, h, alea, Math.max(1, (w * h) / 70), 0xFF16161A, true);
				if (w >= 5 && h >= 5) { // rivets aux coins
					poser(tex, x0 + 1, y0 + 1, 0xFF8A8A92);
					poser(tex, x0 + w - 2, y0 + 1, 0xFF8A8A92);
				}
			}
			case LAME -> {
				// Runes lumineuses gravées au centre de la lame (faces larges uniquement).
				if (h > w && w >= 3) {
					int cx = x0 + w / 2;
					for (int y = 2; y < h - 2; y++) {
						if (y % 4 != 3) {
							poser(tex, cx, y0 + y, 0xFFFF6A1A);
							poser(lueur, cx, y0 + y, 0xFFFF7A2A);
						}
					}
				}
			}
			case TISSU -> {
				// Bas déchiré : quelques pixels transparents sur la dernière ligne des grandes faces.
				if (h > 10) {
					for (int x = 0; x < w; x++) {
						if (alea.nextInt(3) == 0) poser(tex, x0 + x, y0 + h - 1, 0x00000000);
						if (alea.nextInt(6) == 0) poser(tex, x0 + x, y0 + h - 2, 0x00000000);
					}
				}
			}
			default -> { }
		}
	}

	private static int couleurDeBase(Matiere m, Random alea, int x, int y, int w, int h, Face face) {
		return switch (m) {
			case CENDRE -> bruit(alea, 0xFF6E6862, 18);
			case BRAISE -> {
				// Cœur incandescent : jaune au centre, rouge sur les bords.
				double cx = (w - 1) / 2.0, cy = (h - 1) / 2.0;
				double dist = Math.hypot((x - cx) / Math.max(1, cx), (y - cy) / Math.max(1, cy));
				yield dist < 0.5 ? 0xFFFFE070 : (dist < 1.0 ? 0xFFFF8A1E : 0xFFD8401A);
			}
			case ARMURE -> bruit(alea, y % 5 == 0 ? 0xFF34343C : 0xFF45454F, 10);
			case OR -> bruit(alea, (x + y) % 6 == 0 ? 0xFFB08A30 : 0xFF8A6A26, 12);
			case TISSU -> bruit(alea, x % 3 == 0 ? 0xFF4A1414 : 0xFF5E1A18, 8);
			case LAME -> bruit(alea, (x == 0 || x == w - 1) ? 0xFFB8BEC8 : 0xFF6A707C, 8);
			case CUIR -> bruit(alea, 0xFF3A2418, 10);
		};
	}

	/** Trace des fissures sombres ; certaines rougeoient (et vont dans la glowmask). */
	private static void fissures(BufferedImage tex, BufferedImage lueur, int x0, int y0, int w, int h, Random alea, int nombre, int sombre, boolean braises) {
		for (int n = 0; n < nombre; n++) {
			int x = alea.nextInt(w), y = alea.nextInt(h);
			boolean ardente = braises && alea.nextInt(3) == 0;
			int longueur = 2 + alea.nextInt(4);
			for (int i = 0; i < longueur; i++) {
				if (x < 0 || y < 0 || x >= w || y >= h) break;
				if (ardente) {
					int braise = alea.nextBoolean() ? 0xFFFF7A1A : 0xFFFFB040;
					poser(tex, x0 + x, y0 + y, braise);
					poser(lueur, x0 + x, y0 + y, braise);
				} else {
					poser(tex, x0 + x, y0 + y, sombre);
				}
				x += alea.nextInt(3) - 1;
				y += 1;
			}
		}
	}

	// =================================================================================
	//  OBJETS (icônes 16x16)
	// =================================================================================

	private static final int TRANSPARENT = 0x00000000;
	private static final int CONTOUR = 0xFF1A1418;

	/** Couleurs de chaque rune : aucune, flamme, vigueur, givre, foudre. */
	private static final String[] NOMS_RUNES = {"flamme", "vigueur", "givre", "foudre"};
	private static final int[] COULEURS_RUNES = {0xFFFF7A1A, 0xFF4AE04A, 0xFF6AE0FF, 0xFFFFE04A};
	private static final int[] COULEURS_RUNES_SOMBRES = {0xFFB03A0A, 0xFF1E8A2A, 0xFF2A82B0, 0xFFB08A10};

	private static void genererEpees() throws IOException {
		ecrireImage(dessinerEpee(0xFF6A6A72, 0xFF3A3A42), "textures/item/epee_du_veilleur.png");
		for (int i = 0; i < NOMS_RUNES.length; i++) {
			ecrireImage(dessinerEpee(COULEURS_RUNES[i], COULEURS_RUNES_SOMBRES[i]), "textures/item/epee_du_veilleur_" + NOMS_RUNES[i] + ".png");
		}
	}

	/** Épée en diagonale (pointe en haut à droite), dessinée à partir de formes géométriques. */
	private static BufferedImage dessinerEpee(int rune, int runeSombre) {
		BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		// Lame : segment de (4.5, 11.5) à (14.5, 1.5).
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				double px = x + 0.5, py = y + 0.5;
				double[] dt = distanceSegment(px, py, 4.5, 11.5, 14.6, 1.4);
				double dist = dt[0], t = dt[1], cote = dt[2];
				if (t < 0 || t > 1.02) continue;
				if (dist < 0.55) poser(img, x, y, t > 0.15 && t < 0.85 ? rune : 0xFF8A8F99);
				else if (dist < 1.25) poser(img, x, y, cote > 0 ? 0xFFDDE2EA : 0xFF4A4E5A);
				else if (dist < 1.9 && t > 0.05) poser(img, x, y, CONTOUR);
			}
		}
		// Garde dorée en forme d'ailes, perpendiculaire à la lame.
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				double[] dt = distanceSegment(x + 0.5, y + 0.5, 1.8, 8.6, 7.6, 14.4);
				if (dt[1] >= 0 && dt[1] <= 1) {
					if (dt[0] < 0.7) poser(img, x, y, dt[2] > 0 ? 0xFFF0C050 : 0xFFB08020);
					else if (dt[0] < 1.4) poser(img, x, y, CONTOUR);
				}
			}
		}
		// Gemme de rune au centre de la garde.
		poser(img, 4, 11, rune);
		poser(img, 5, 11, runeSombre);
		poser(img, 4, 12, runeSombre);
		// Poignée et pommeau.
		int[][] poignee = {{3, 12}, {2, 13}, {3, 13}, {2, 12}};
		for (int[] p : poignee) poser(img, p[0], p[1], 0xFF5A3420);
		poser(img, 3, 12, 0xFF7A4A2C);
		poser(img, 1, 14, 0xFFC02030);
		poser(img, 0, 15, 0xFF701018);
		poser(img, 1, 15, 0xFF901820);
		poser(img, 0, 14, CONTOUR);
		poser(img, 2, 14, CONTOUR);
		return img;
	}

	/** Glyphes des runes (6x7), dessinés en ASCII : '#' = trait. */
	private static final String[][] GLYPHES = {
		{"..#...", ".###..", "#.#.#.", "..#...", ".#.#..", "#...#.", "......"},  // flamme
		{"..#...", "#.#.#.", ".###..", "..#...", "..#...", ".###..", "......"},  // vigueur
		{"#...#.", ".#.#..", "..#...", "#####.", "..#...", ".#.#..", "#...#."},  // givre
		{"...##.", "..##..", ".####.", "...##.", "..##..", ".##...", "##...."}   // foudre
	};

	private static void genererRunes() throws IOException {
		for (int i = 0; i < NOMS_RUNES.length; i++) {
			BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
			Random alea = new Random(100 + i);
			// Pierre : rectangle aux coins arrondis.
			for (int y = 1; y < 15; y++) {
				for (int x = 2; x < 14; x++) {
					boolean coin = (x == 2 || x == 13) && (y == 1 || y == 14);
					if (coin) continue;
					boolean bord = x == 2 || x == 13 || y == 1 || y == 14 || ((x == 3 || x == 12) && (y == 1 || y == 14));
					poser(img, x, y, bord ? CONTOUR : bruit(alea, y < 4 ? 0xFF5A5A64 : 0xFF46464F, 8));
				}
			}
			// Glyphe lumineux + halo.
			String[] g = GLYPHES[i];
			for (int y = 0; y < g.length; y++) {
				for (int x = 0; x < g[y].length(); x++) {
					if (g[y].charAt(x) == '#') {
						poser(img, 5 + x, 4 + y, COULEURS_RUNES[i]);
						if (y + 1 < g.length && g[y + 1].charAt(x) != '#') poser(img, 5 + x, 5 + y, COULEURS_RUNES_SOMBRES[i]);
					}
				}
			}
			ecrireImage(img, "textures/item/rune_de_" + NOMS_RUNES[i] + ".png");
		}
	}

	private static void genererObjets() throws IOException {
		// Cendre ardente : petit tas de cendre piqué de braises.
		BufferedImage cendre = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Random alea = new Random(5);
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				double dx = (x - 7.5) / 6.5, dy = (y - 11.0) / 4.5;
				double r = dx * dx + dy * dy;
				if (y >= 6 && r <= 1.0) {
					int c = bruit(alea, y < 9 ? 0xFF8A847E : 0xFF5E5852, 14);
					if (r > 0.8) c = 0xFF3A3532;
					if (alea.nextInt(9) == 0 && r < 0.7) c = alea.nextBoolean() ? 0xFFFF7A1A : 0xFFFFC040;
					poser(cendre, x, y, c);
				}
			}
		}
		ecrireImage(cendre, "textures/item/cendre_ardente.png");

		// Fiole de cendre froide : flacon rond, contenu gris-bleu.
		BufferedImage fiole = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				double dx = (x - 7.5) / 5.0, dy = (y - 10.0) / 5.0;
				double r = dx * dx + dy * dy;
				if (r <= 1.0) {
					int c = r > 0.75 ? 0xFFB8D0DC : (y >= 9 ? bruit(alea, 0xFF7A8EA0, 10) : 0x66C8E0F0);
					if (x == 5 && y == 7) c = 0xFFFFFFFF;
					poser(fiole, x, y, c);
				}
			}
		}
		for (int y = 1; y < 6; y++) {
			for (int x = 6; x <= 9; x++) {
				poser(fiole, x, y, (x == 6 || x == 9) ? 0xFFB8D0DC : 0x66C8E0F0);
			}
		}
		for (int x = 5; x <= 10; x++) poser(fiole, x, 1, 0xFF6A4A2A); // bouchon
		for (int x = 6; x <= 9; x++) poser(fiole, x, 0, 0xFF8A6A3A);
		ecrireImage(fiole, "textures/item/fiole_de_cendre_froide.png");

		// Sceau du Gardien : médaillon de fer noir, centre rouge, croix gravée.
		BufferedImage sceau = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				double r = Math.hypot(x - 7.5, y - 7.5);
				if (r <= 7.3) {
					int c;
					if (r > 6.4) c = CONTOUR;
					else if (r > 4.6) c = bruit(alea, 0xFF4A4A52, 10);
					else if (r > 3.6) c = 0xFF8A6A26;
					else c = bruit(alea, 0xFF8A1A1A, 14);
					if (r <= 3.6 && (x == 7 || x == 8 || y == 7 || y == 8)) c = 0xFFFF9A3A;
					poser(sceau, x, y, c);
				}
			}
		}
		ecrireImage(sceau, "textures/item/sceau_du_gardien.png");

		// Premier Éclat : cristal rouge en losange, avec reflets.
		BufferedImage eclat = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				double dx = Math.abs(x - 7.5) / 5.0, dy = Math.abs(y - 7.5) / 7.5;
				double r = dx + dy;
				if (r <= 1.0) {
					int c;
					if (r > 0.86) c = 0xFF4A0810;
					else if (x < 7.5 && y < 7.5) c = 0xFFFF5A5A;
					else if (x >= 7.5 && y >= 7.5) c = 0xFF8A1020;
					else c = 0xFFD02030;
					if (r < 0.3) c = 0xFFFFB0A0;
					poser(eclat, x, y, c);
				}
			}
		}
		poser(eclat, 5, 4, 0xFFFFFFFF);
		poser(eclat, 6, 3, 0xFFFFE0E0);
		ecrireImage(eclat, "textures/item/premier_eclat.png");
	}

	private static void genererOeufs() throws IOException {
		ecrireImage(dessinerOeuf(0xFF6E6862, 0xFFFF7A1A, 21), "textures/item/cendreux_spawn_egg.png");
		ecrireImage(dessinerOeuf(0xFF3A3A44, 0xFFB08A30, 22), "textures/item/premier_gardien_spawn_egg.png");
	}

	private static BufferedImage dessinerOeuf(int fond, int taches, long graine) {
		BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Random alea = new Random(graine);
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				double dx = (x - 7.5) / (y < 8 ? 4.6 : 5.6), dy = (y - 8.5) / 7.0;
				double r = dx * dx + dy * dy;
				if (r <= 1.0) {
					int c = r > 0.78 ? CONTOUR : fond;
					if (r <= 0.78 && alea.nextInt(5) == 0) c = taches;
					if (r <= 0.78 && x < 6 && y < 7 && y > 3) c = eclaircir(c, 1.25F);
					poser(img, x, y, c);
				}
			}
		}
		return img;
	}

	// =================================================================================
	//  BLOC ET INTERFACE DE LA FORGE RUNIQUE
	// =================================================================================

	private static void genererBlocForge() throws IOException {
		Random alea = new Random(9);
		BufferedImage cote = briques(alea);
		ecrireImage(cote, "textures/block/forge_runique_side.png");

		// Face avant : briques + grand glyphe rougeoyant au centre.
		BufferedImage avant = briques(alea);
		String[] glyphe = {"...##...", "..####..", ".##..##.", "##.##.##", "...##...", "..#..#..", ".#....#."};
		for (int y = 0; y < glyphe.length; y++) {
			for (int x = 0; x < 8; x++) {
				if (glyphe[y].charAt(x) == '#') poser(avant, 4 + x, 4 + y, (x + y) % 2 == 0 ? 0xFFFF8A1E : 0xFFFFB040);
			}
		}
		ecrireImage(avant, "textures/block/forge_runique_front.png");

		// Dessus : plaque de fer noir avec un creuset de braises au centre.
		BufferedImage dessus = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				int c = bruit(alea, 0xFF2E2E34, 8);
				if (x == 0 || y == 0 || x == 15 || y == 15) c = 0xFF1A1A1E;
				double r = Math.hypot(x - 7.5, y - 7.5);
				if (r < 4.2) c = r < 3.0 ? (alea.nextBoolean() ? 0xFFFF7A1A : 0xFFFFB040) : 0xFF5A1E0A;
				if ((x == 2 || x == 13) && (y == 2 || y == 13)) c = 0xFF8A8A92;
				poser(dessus, x, y, c);
			}
		}
		ecrireImage(dessus, "textures/block/forge_runique_top.png");

		BufferedImage dessous = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) poser(dessous, x, y, bruit(alea, 0xFF3A3A40, 8));
		ecrireImage(dessous, "textures/block/forge_runique_bottom.png");
	}

	/** Briques de deepslate sombres. */
	private static BufferedImage briques(Random alea) {
		BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				int rangee = y / 4;
				int decalage = (rangee % 2) * 4;
				boolean joint = y % 4 == 3 || (x + decalage) % 8 == 7;
				int c = joint ? 0xFF16161A : bruit(alea, y % 4 == 0 ? 0xFF4A4A54 : 0xFF3A3A44, 8);
				poser(img, x, y, c);
			}
		}
		return img;
	}

	/** Interface 176x166 (dans une image 256x256), style Minecraft avec des tons de forge. */
	private static void genererInterfaceForge() throws IOException {
		BufferedImage img = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
		int fond = 0xFFC6B8A8, clair = 0xFFFFF4E4, sombre = 0xFF6A5444;
		// Panneau avec bordure biseautée.
		for (int y = 0; y < 166; y++) {
			for (int x = 0; x < 176; x++) {
				boolean coin = (x < 2 || x > 173) && (y < 2 || y > 163);
				if (coin) continue;
				int c = fond;
				if (x == 0 || y == 0 || x == 175 || y == 165) c = 0xFF000000;
				else if (x <= 2 || y <= 2) c = clair;
				else if (x >= 173 || y >= 163) c = sombre;
				poser(img, x, y, c);
			}
		}
		// Bandeau décoratif sombre derrière la zone de gravure.
		for (int y = 34; y < 74; y++) {
			for (int x = 8; x < 168; x++) {
				poser(img, x, y, (x + y) % 7 == 0 ? 0xFFB0A090 : 0xFFBCAE9E);
			}
		}
		// Emplacements : épée, rune, résultat (le résultat est plus grand, comme l'enclume).
		emplacement(img, 26, 46, 18);
		emplacement(img, 75, 46, 18);
		emplacement(img, 129, 42, 26);
		// "+" entre l'épée et la rune.
		for (int i = -3; i <= 3; i++) {
			poser(img, 54 + i, 55, 0xFF6A5444);
			poser(img, 54, 55 + i, 0xFF6A5444);
		}
		// Flèche vers le résultat.
		for (int x = 100; x <= 120; x++) {
			for (int y = 53; y <= 57; y++) poser(img, x, y, 0xFF8A2A10);
		}
		for (int i = 0; i <= 6; i++) {
			for (int y = 55 - (6 - i); y <= 55 + (6 - i); y++) poser(img, 121 + i, y, 0xFF8A2A10);
		}
		// Inventaire du joueur (3 rangées) et barre d'action.
		for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) emplacement(img, 7 + c * 18, 83 + r * 18, 18);
		for (int c = 0; c < 9; c++) emplacement(img, 7 + c * 18, 141, 18);
		ecrireImage(img, "textures/gui/container/forge_runique.png");
	}

	private static void emplacement(BufferedImage img, int x0, int y0, int taille) {
		for (int y = 0; y < taille; y++) {
			for (int x = 0; x < taille; x++) {
				int c = 0xFF8B8B8B;
				if (x == 0 || y == 0) c = 0xFF373737;
				else if (x == taille - 1 || y == taille - 1) c = 0xFFFFFFFF;
				poser(img, x0 + x, y0 + y, c);
			}
		}
	}

	// =================================================================================
	//  OUTILS
	// =================================================================================

	/** Distance d'un point à un segment, position t le long du segment, et côté (signe). */
	private static double[] distanceSegment(double px, double py, double ax, double ay, double bx, double by) {
		double vx = bx - ax, vy = by - ay;
		double longueur2 = vx * vx + vy * vy;
		double t = ((px - ax) * vx + (py - ay) * vy) / longueur2;
		double qx = ax + t * vx, qy = ay + t * vy;
		double cote = vx * (py - ay) - vy * (px - ax);
		return new double[] {Math.hypot(px - qx, py - qy), t, -cote};
	}

	private static int bruit(Random alea, int couleur, int amplitude) {
		int delta = alea.nextInt(amplitude * 2 + 1) - amplitude;
		int r = borne(((couleur >> 16) & 0xFF) + delta);
		int v = borne(((couleur >> 8) & 0xFF) + delta);
		int b = borne((couleur & 0xFF) + delta);
		return (couleur & 0xFF000000) | (r << 16) | (v << 8) | b;
	}

	private static int eclaircir(int couleur, float facteur) {
		int r = borne((int) (((couleur >> 16) & 0xFF) * facteur));
		int v = borne((int) (((couleur >> 8) & 0xFF) * facteur));
		int b = borne((int) ((couleur & 0xFF) * facteur));
		return (couleur & 0xFF000000) | (r << 16) | (v << 8) | b;
	}

	private static int borne(int valeur) {
		return Math.max(0, Math.min(255, valeur));
	}

	private static void poser(BufferedImage img, int x, int y, int couleur) {
		if (x >= 0 && y >= 0 && x < img.getWidth() && y < img.getHeight()) {
			img.setRGB(x, y, couleur);
		}
	}

	private static String nb(double valeur) {
		return valeur == Math.rint(valeur) ? String.valueOf((long) valeur) : String.valueOf(valeur);
	}

	private static String vec(double... valeurs) {
		StringBuilder sb = new StringBuilder("[");
		for (int i = 0; i < valeurs.length; i++) {
			sb.append(nb(valeurs[i]));
			if (i < valeurs.length - 1) sb.append(", ");
		}
		return sb.append("]").toString();
	}

	private static void ecrireImage(BufferedImage img, String chemin) throws IOException {
		Path fichier = ASSETS.resolve(chemin);
		Files.createDirectories(fichier.getParent());
		ImageIO.write(img, "png", fichier.toFile());
	}

	private static void ecrireTexte(String texte, String chemin) throws IOException {
		Path fichier = ASSETS.resolve(chemin);
		Files.createDirectories(fichier.getParent());
		Files.writeString(fichier, texte);
	}
}
