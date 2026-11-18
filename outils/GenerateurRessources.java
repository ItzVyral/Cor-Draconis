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
 * Les animations (geckolib/animations/entity/*.animation.json) sont écrites à la main :
 * l'outil n'y touche pas. Si vous renommez un os ici, renommez-le aussi dans les animations.
 *
 * ATTENTION : relancer l'outil ÉCRASE les autres fichiers. Une fois que vous retouchez un modèle
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

	/**
	 * Matières : chacune a une "rampe" de couleurs, de la plus sombre à la plus claire.
	 * La peinture choisit une couleur de la rampe selon l'éclairage de chaque pixel.
	 *
	 * NOTION JAVA — une enum peut avoir des champs et un constructeur, comme une classe.
	 */
	enum Matiere {
		//          rampe (sombre -> clair)                                                                          grain  brille à partir de
		CENDRE(new int[] {0xFF1B1716, 0xFF2E2826, 0xFF4A423E, 0xFF6D645D, 0xFF958B82, 0xFFBDB3A8}, 0.14, -1),
		CHARBON(new int[] {0xFF0C0A0A, 0xFF171313, 0xFF241E1D, 0xFF332B29, 0xFF453B38}, 0.12, -1),
		GRIFFE(new int[] {0xFF0C0A0A, 0xFF171313, 0xFF241E1D, 0xFF332B29, 0xFF453B38}, 0.10, -1),
		COTE(new int[] {0xFF2E2824, 0xFF554C45, 0xFF847A70, 0xFFB2A898, 0xFFDAD2C2}, 0.10, -1),
		BRAISE(new int[] {0xFFFFFFFF}, 0.0, 0),
		CAVITE(new int[] {0xFF140404, 0xFF2E0806, 0xFF5A1206, 0xFF8E2408, 0xFFC8400C, 0xFFFF7A1A}, 0.10, 3),
		CRISTAL(new int[] {0xFF3E0E06, 0xFF7A2008, 0xFFC0480C, 0xFFF07A1E, 0xFFFFB45A, 0xFFFFE8B8}, 0.08, 3),
		ARMURE(new int[] {0xFF0E0F15, 0xFF181A23, 0xFF242733, 0xFF323646, 0xFF444A5E, 0xFF5E6580, 0xFF8890AA}, 0.06, -1),
		OR(new int[] {0xFF2A1806, 0xFF4A2E0C, 0xFF744C14, 0xFFA2701E, 0xFFCE9A30, 0xFFF0C850, 0xFFFFF0A0}, 0.06, -1),
		TISSU(new int[] {0xFF12040A, 0xFF280810, 0xFF420E16, 0xFF62161C, 0xFF861F22, 0xFFA8302A}, 0.08, -1),
		TISSU_DECHIRE(new int[] {0xFF12040A, 0xFF280810, 0xFF420E16, 0xFF62161C, 0xFF861F22, 0xFFA8302A}, 0.08, -1),
		LAME(new int[] {0xFF1A1E26, 0xFF323844, 0xFF525A6A, 0xFF7C8698, 0xFFAAB4C6, 0xFFD6DEEC, 0xFFFFFFFF}, 0.05, -1),
		CUIR(new int[] {0xFF140A06, 0xFF26160E, 0xFF3C2416, 0xFF543420, 0xFF6E4830}, 0.08, -1),
		MAILLE(new int[] {0xFF101218, 0xFF20232C, 0xFF363A48, 0xFF525868, 0xFF7A8294}, 0.06, -1);

		final int[] rampe;
		/** Variation aléatoire de lumière d'un pixel à l'autre. */
		final double grain;
		/** Indice de la rampe à partir duquel le pixel brille dans le noir (-1 : jamais). */
		final int brillance;

		Matiere(final int[] rampe, final double grain, final int brillance) {
			this.rampe = rampe;
			this.grain = grain;
			this.brillance = brillance;
		}
	}

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

		/** Ajoute un cube droit. NOTION JAVA — "return this" permet d'enchaîner les appels. */
		Os cube(double ox, double oy, double oz, int sx, int sy, int sz, Matiere m) {
			cubes.add(new Cube(ox, oy, oz, sx, sy, sz, m, null, null));
			return this;
		}

		/** Ajoute un cube tourné (pivot px,py,pz ; angles rx,ry,rz en degrés). */
		Os cubeTourne(double ox, double oy, double oz, int sx, int sy, int sz, Matiere m,
				double px, double py, double pz, double rx, double ry, double rz) {
			cubes.add(new Cube(ox, oy, oz, sx, sy, sz, m, new double[] {px, py, pz}, new double[] {rx, ry, rz}));
			return this;
		}

		/** Copie symétrique (gauche <-> droite) d'un os : x devient -x. */
		static Os miroir(Os source, String nom, String parent) {
			Os copie = new Os(nom, parent, -source.pivot[0], source.pivot[1], source.pivot[2]);
			for (Cube c : source.cubes) {
				double[] pivot = c.pivot == null ? null : new double[] {-c.pivot[0], c.pivot[1], c.pivot[2]};
				double[] rotation = c.rotation == null ? null : new double[] {c.rotation[0], -c.rotation[1], -c.rotation[2]};
				copie.cubes.add(new Cube(-(c.ox + c.sx), c.oy, c.oz, c.sx, c.sy, c.sz, c.matiere, pivot, rotation));
			}
			return copie;
		}
	}

	// --- Le Cendreux ------------------------------------------------------------------

	/**
	 * Une goule de cendre voûtée : longs bras griffus, cage thoracique ouverte sur un cœur
	 * de braise, crâne fendu avec une mâchoire mobile, cristaux de cendre ardente dans le dos.
	 * Les "fragments" flottent autour de lui ; le "tas" n'apparaît que lorsqu'il s'effondre.
	 */
	private static void genererCendreux() throws IOException {
		List<Os> os = new ArrayList<>();
		os.add(new Os("racine", null, 0, 0, 0));

		// Jambes (cuisse + tibia, pour plier le genou).
		Os jambeDroite = new Os("jambe_droite", "racine", -2.5, 13, 0.5)
			.cube(-4.5, 7, -1.5, 4, 6, 4, Matiere.CENDRE);
		Os tibiaDroit = new Os("tibia_droit", "jambe_droite", -2.5, 7, 0.5)
			.cube(-4, 1, -1, 3, 6, 3, Matiere.CHARBON)
			.cube(-4.5, 0, -2.5, 4, 2, 4, Matiere.CHARBON)
			.cube(-4.5, 0, -3.5, 1, 1, 1, Matiere.GRIFFE)
			.cube(-2.5, 0, -3.5, 1, 1, 1, Matiere.GRIFFE);
		os.add(jambeDroite);
		os.add(tibiaDroit);
		os.add(Os.miroir(jambeDroite, "jambe_gauche", "racine"));
		os.add(Os.miroir(tibiaDroit, "tibia_gauche", "jambe_gauche"));

		// Corps : bassin, taille fine, cage thoracique creuse avec côtes, bosse et cristaux.
		os.add(new Os("corps", "racine", 0, 13, 0.5)
			.cube(-4, 12, -1.5, 8, 4, 4, Matiere.CHARBON)
			.cube(-3.5, 16, -1, 7, 4, 4, Matiere.CENDRE)
			.cube(-5.5, 20, 0, 11, 8, 4, Matiere.CENDRE)
			.cube(-5.5, 20, -3, 2, 8, 3, Matiere.CENDRE)
			.cube(3.5, 20, -3, 2, 8, 3, Matiere.CENDRE)
			.cube(-5.5, 27, -3, 11, 2, 3, Matiere.CENDRE)
			.cube(-3.5, 20, -0.5, 7, 7, 1, Matiere.CAVITE)
			.cube(-3.5, 21, -3.2, 7, 1, 1, Matiere.COTE)
			.cube(-3.5, 23, -3.2, 7, 1, 1, Matiere.COTE)
			.cube(-3.5, 25, -3.2, 7, 1, 1, Matiere.COTE)
			.cubeTourne(-5, 26, 0.5, 10, 4, 5, Matiere.CENDRE, 0, 26, 3, -15, 0, 0)
			.cubeTourne(-3.5, 29, 2.5, 2, 5, 2, Matiere.CRISTAL, -2.5, 29, 3.5, -35, 0, 20)
			.cubeTourne(1.5, 29, 2, 2, 6, 2, Matiere.CRISTAL, 2.5, 29, 3, -40, 0, -15)
			.cubeTourne(-0.5, 28, 4, 1, 4, 1, Matiere.CRISTAL, 0, 28, 4.5, -60, 0, 0)
			.cubeTourne(3.5, 27, 2, 1, 3, 1, Matiere.CENDRE, 4, 27, 2.5, -20, 0, -35));
		os.add(new Os("coeur", "corps", 0, 23.5, -1.5)
			.cube(-1.5, 22, -2.5, 3, 3, 2, Matiere.BRAISE));

		// "regard" ne contient aucun cube : le rendu le tourne vers la cible (suivi de la tête).
		os.add(new Os("regard", "corps", 0, 28.5, -1.5));
		os.add(new Os("tete", "regard", 0, 28.5, -1.5)
			.cube(-3.5, 29, -6, 7, 6, 7, Matiere.CENDRE)
			.cube(-4, 33, -6.6, 8, 2, 2, Matiere.CHARBON)
			.cube(-2.5, 29, -6.3, 5, 1, 1, Matiere.BRAISE)
			.cubeTourne(-3, 35, -4, 1, 3, 1, Matiere.CENDRE, -2.5, 35, -3.5, 0, 0, 15)
			.cubeTourne(-1, 35, -5, 1, 2, 1, Matiere.CHARBON, -0.5, 35, -4.5, -10, 0, 0)
			.cubeTourne(1.5, 35, -3.5, 1, 4, 1, Matiere.CENDRE, 2, 35, -3, -10, 0, -15)
			.cubeTourne(-1, 35, -1.5, 2, 2, 2, Matiere.CRISTAL, 0, 35, -0.5, -20, 0, 10));
		os.add(new Os("yeux", "tete", 0, 31.5, -6.3)
			.cube(-3, 31, -6.4, 2, 1, 1, Matiere.BRAISE)
			.cube(1, 31, -6.4, 2, 1, 1, Matiere.BRAISE));
		os.add(new Os("machoire", "tete", 0, 29.5, 0)
			.cube(-3, 27.5, -6, 6, 2, 6, Matiere.CHARBON)
			.cube(-2.5, 29.5, -6.2, 1, 1, 1, Matiere.COTE)
			.cube(1.5, 29.5, -6.2, 1, 1, 1, Matiere.COTE));

		// Bras (épaule + bras, puis avant-bras avec poing et griffes).
		Os brasDroit = new Os("bras_droit", "corps", -5.5, 26.5, 0.5)
			.cube(-8.5, 24, -2, 4, 4, 5, Matiere.CENDRE)
			.cubeTourne(-8, 27.5, -0.5, 2, 3, 2, Matiere.CRISTAL, -7, 28, 0.5, 0, 0, 25)
			.cube(-7.5, 17, -1, 3, 7, 3, Matiere.CENDRE);
		Os avantBrasDroit = new Os("avant_bras_droit", "bras_droit", -6, 17.5, 0.5)
			.cube(-8, 10, -1.5, 4, 8, 4, Matiere.CENDRE)
			.cubeTourne(-8.5, 14, 0, 1, 3, 1, Matiere.CENDRE, -8, 14, 0.5, 0, 0, 30)
			.cube(-8, 8, -2, 4, 2, 4, Matiere.CHARBON)
			.cube(-8, 5, -2, 1, 3, 1, Matiere.GRIFFE)
			.cube(-6.5, 5, -2.5, 1, 3, 1, Matiere.GRIFFE)
			.cube(-5, 5, -2, 1, 3, 1, Matiere.GRIFFE);
		os.add(brasDroit);
		os.add(avantBrasDroit);
		os.add(Os.miroir(brasDroit, "bras_gauche", "corps"));
		os.add(Os.miroir(avantBrasDroit, "avant_bras_gauche", "bras_gauche"));

		// Fragments de cendre qui tournent autour de lui (animation "ambiance").
		os.add(new Os("fragments", "racine", 0, 18, 0.5)
			.cubeTourne(-11, 22, -0.5, 2, 2, 2, Matiere.CENDRE, -10, 23, 0.5, 45, 30, 0)
			.cubeTourne(8.5, 16, -4, 1, 1, 1, Matiere.CRISTAL, 9, 16.5, -3.5, 30, 45, 0)
			.cubeTourne(-3, 12, 8.5, 2, 1, 2, Matiere.CENDRE, -2, 12.5, 9.5, 0, 45, 20)
			.cubeTourne(6, 25, 6, 1, 2, 1, Matiere.CHARBON, 6.5, 26, 6.5, 20, 0, 30)
			.cubeTourne(-8, 14, -7, 1, 1, 1, Matiere.BRAISE, -7.5, 14.5, -6.5, 45, 45, 0)
			.cubeTourne(3, 9, -9.5, 1, 1, 1, Matiere.CENDRE, 3.5, 9.5, -9, 30, 0, 45));

		// Tas de cendre (invisible tant qu'il tient debout) et le cœur posé dessus.
		os.add(new Os("tas", "racine", 0, 0, 0.5)
			.cube(-6, 0, -5.5, 12, 2, 12, Matiere.CENDRE)
			.cube(-4.5, 2, -4, 9, 2, 9, Matiere.CENDRE)
			.cube(-3, 4, -2.5, 6, 1, 6, Matiere.CENDRE)
			.cube(-7.5, 0, 2, 3, 1, 3, Matiere.CENDRE)
			.cube(4, 0, -6.5, 3, 1, 2, Matiere.CHARBON)
			.cubeTourne(4.5, 0, 3, 2, 2, 5, Matiere.CHARBON, 5.5, 1, 5.5, 0, 30, 0)
			.cubeTourne(-5.5, 1, -6.5, 4, 3, 4, Matiere.CENDRE, -3.5, 2.5, -4.5, 0, -20, 10));
		os.add(new Os("coeur_tas", "tas", 0, 6, 0.5)
			.cube(-1.5, 4.5, -1, 3, 3, 3, Matiere.BRAISE));

		ecrireModele("cendreux", os, 128, 36.0, 2.0, 3.0, 1337L);
	}

	// --- Ser Aldric, Premier Gardien --------------------------------------------------

	/**
	 * Un chevalier colossal rongé par la Cendre : armure noire à liserés d'or, heaume à cornes
	 * de dragon et plumet, épaulières à pointes, cape en deux pans, épée à garde ailée gravée
	 * de runes, cristaux de cendre ardente qui percent l'armure et gravitent autour de lui.
	 */
	private static void genererPremierGardien() throws IOException {
		List<Os> os = new ArrayList<>();
		os.add(new Os("racine", null, 0, 0, 0));

		os.add(new Os("bassin", "racine", 0, 24, 0)
			.cube(-6.5, 22, -3.5, 13, 5, 7, Matiere.ARMURE)
			.cube(-7, 26, -4, 14, 2, 8, Matiere.CUIR)
			.cube(-2, 25.5, -4.6, 4, 3, 1, Matiere.OR)
			.cubeTourne(-7, 17, -4.3, 5, 6, 1, Matiere.ARMURE, -4.5, 23, -4, -10, 0, 0)
			.cubeTourne(2, 17, -4.3, 5, 6, 1, Matiere.ARMURE, 4.5, 23, -4, -10, 0, 0)
			.cube(-7.6, 18, -2.5, 1, 6, 5, Matiere.ARMURE)
			.cube(6.6, 18, -2.5, 1, 6, 5, Matiere.ARMURE));
		os.add(new Os("tabard", "bassin", 0, 26, -4.4)
			.cube(-2.5, 11, -4.9, 5, 15, 1, Matiere.TISSU_DECHIRE));

		Os jambeDroite = new Os("jambe_droite", "bassin", -3.5, 22, 0)
			.cube(-6, 12, -2.5, 5, 10, 5, Matiere.MAILLE)
			.cube(-6.5, 14, -3.2, 6, 7, 1, Matiere.ARMURE);
		Os tibiaDroit = new Os("tibia_droit", "jambe_droite", -3.5, 12, 0)
			.cube(-6, 3, -3, 5, 9, 6, Matiere.ARMURE)
			.cube(-6.5, 9.5, -4, 6, 4, 2, Matiere.ARMURE)
			.cube(-4.5, 10.5, -5, 2, 2, 1, Matiere.OR)
			.cube(-6.5, 0, -5, 6, 3, 8, Matiere.ARMURE)
			.cube(-5.5, 0, -6, 4, 2, 1, Matiere.ARMURE);
		os.add(jambeDroite);
		os.add(tibiaDroit);
		os.add(Os.miroir(jambeDroite, "jambe_gauche", "bassin"));
		os.add(Os.miroir(tibiaDroit, "tibia_gauche", "jambe_gauche"));

		os.add(new Os("torse", "bassin", 0, 27, 0)
			.cube(-5.5, 27, -3, 11, 6, 6, Matiere.MAILLE)
			.cube(-6, 29, -3.7, 12, 4, 1, Matiere.ARMURE)
			.cube(-7.5, 33, -4.5, 15, 11, 9, Matiere.ARMURE)
			.cube(-3.5, 35, -5.3, 7, 7, 1, Matiere.OR)
			.cube(-5, 43, -4, 10, 3, 8, Matiere.OR)
			.cubeTourne(3, 41, 3, 2, 4, 2, Matiere.CRISTAL, 4, 41, 4, -25, 0, -20)
			.cubeTourne(-5, 38, 3.5, 2, 3, 2, Matiere.CRISTAL, -4, 38, 4.5, -35, 0, 15)
			.cubeTourne(4.5, 34, -5.5, 2, 2, 1, Matiere.CRISTAL, 5.5, 35, -5, 0, 0, 20));
		os.add(new Os("coeur", "torse", 0, 38.5, -5.8)
			.cube(-1.5, 37, -6.3, 3, 3, 1, Matiere.BRAISE));
		os.add(new Os("cape", "torse", 0, 44, 4.5)
			.cube(-7, 30, 4.5, 14, 14, 1, Matiere.TISSU));
		os.add(new Os("cape_bas", "cape", 0, 30, 5)
			.cube(-7, 13, 4.5, 14, 17, 1, Matiere.TISSU_DECHIRE));

		os.add(new Os("regard", "torse", 0, 45, 0));
		Os tete = new Os("tete", "regard", 0, 45, 0)
			.cube(-4.5, 45, -4.5, 9, 9, 9, Matiere.ARMURE)
			.cube(-4, 46, -5.5, 8, 6, 1, Matiere.ARMURE)
			.cube(-5, 52, -5, 10, 1, 10, Matiere.OR)
			.cube(-0.5, 54, -4.5, 1, 2, 9, Matiere.OR);
		// Cornes de dragon (3 segments chacune, la gauche est le miroir de la droite).
		Os corne = new Os("corne", "tete", 0, 0, 0)
			.cube(-7.5, 50.5, -1.5, 3, 3, 3, Matiere.COTE)
			.cubeTourne(-8, 53, -1, 2, 4, 2, Matiere.COTE, -7, 53, 0, -30, 0, 20)
			.cubeTourne(-9, 55.5, 1, 1, 3, 1, Matiere.COTE, -8.5, 55.5, 1.5, -55, 0, 30);
		tete.cubes.addAll(corne.cubes);
		tete.cubes.addAll(Os.miroir(corne, "corne", "tete").cubes);
		os.add(tete);
		os.add(new Os("visiere", "tete", 0, 49.5, -5.6)
			.cube(-3, 49, -5.8, 6, 1, 1, Matiere.BRAISE)
			.cube(-0.5, 46.5, -5.8, 1, 2, 1, Matiere.BRAISE));
		os.add(new Os("plumet", "tete", 0, 55, 1)
			.cube(-1, 55, -2, 2, 2, 7, Matiere.TISSU)
			.cube(-1, 50, 4, 2, 6, 2, Matiere.TISSU_DECHIRE));

		Os brasDroit = new Os("bras_droit", "torse", -9.5, 42, 0)
			.cube(-14.5, 39, -5, 8, 5, 10, Matiere.ARMURE)
			.cube(-15, 38.5, -5.5, 9, 1, 11, Matiere.OR)
			.cube(-14, 36, -4.5, 7, 3, 9, Matiere.ARMURE)
			.cubeTourne(-13, 43, -1, 2, 5, 2, Matiere.ARMURE, -12, 43, 0, 0, 0, 25)
			.cubeTourne(-11, 43.5, -3.5, 1, 3, 1, Matiere.OR, -10.5, 43.5, -3, 0, 0, 10)
			.cubeTourne(-12.5, 43, 2, 2, 3, 2, Matiere.CRISTAL, -11.5, 43, 3, -20, 0, 30)
			.cube(-12, 31, -2.5, 5, 8, 5, Matiere.MAILLE);
		Os avantBrasDroit = new Os("avant_bras_droit", "bras_droit", -9.5, 31, 0)
			.cube(-12.5, 22, -3, 6, 9, 6, Matiere.ARMURE)
			.cube(-13, 27.5, -3.5, 7, 3, 7, Matiere.OR)
			.cube(-12, 19, -2.5, 5, 3, 5, Matiere.CUIR);
		os.add(brasDroit);
		os.add(avantBrasDroit);
		os.add(new Os("epee", "avant_bras_droit", -9.5, 20.5, 0)
			.cube(-10.5, 18, -1, 2, 5, 2, Matiere.CUIR)
			.cube(-13, 16, -1, 7, 2, 2, Matiere.OR)
			.cubeTourne(-14.5, 16.5, -1, 2, 3, 2, Matiere.OR, -13.5, 17.5, 0, 0, 0, -30)
			.cubeTourne(-6.5, 16.5, -1, 2, 3, 2, Matiere.OR, -5.5, 17.5, 0, 0, 0, 30)
			.cube(-10, 16.5, -1.3, 1, 1, 1, Matiere.BRAISE)
			.cube(-11, 2, -0.5, 3, 14, 1, Matiere.LAME)
			.cube(-10.5, 0, -0.5, 2, 2, 1, Matiere.LAME));
		os.add(Os.miroir(brasDroit, "bras_gauche", "torse"));
		os.add(Os.miroir(avantBrasDroit, "avant_bras_gauche", "bras_gauche"));

		// Éclats de cendre ardente en orbite (animation "ambiance").
		os.add(new Os("eclats_orbitaux", "racine", 0, 36, 0)
			.cubeTourne(-15, 38, -1, 2, 4, 2, Matiere.CRISTAL, -14, 40, 0, 15, 0, 20)
			.cubeTourne(13, 34, -1, 2, 3, 2, Matiere.CRISTAL, 14, 35.5, 0, -10, 0, -25)
			.cubeTourne(-1, 42, 13, 2, 3, 2, Matiere.CRISTAL, 0, 43.5, 14, 25, 0, 10)
			.cubeTourne(-1, 30, -15, 1, 3, 1, Matiere.CRISTAL, -0.5, 31.5, -14.5, -20, 0, 15));

		ecrireModele("premier_gardien", os, 128, 58.0, 3.0, 4.5, 777L);
	}

	/** Place les cubes dans la texture, peint texture + glowmask, puis écrit le .geo.json. */
	private static void ecrireModele(String nom, List<Os> os, int largeurTex, double hauteurModele, double largeurVisible, double hauteurVisible, long graine) throws IOException {
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
		// Hauteur de texture : juste ce qu'il faut, arrondie au multiple de 16 supérieur.
		int hauteurTex = ((y + hauteurRangee + 15) / 16) * 16;

		// 2. Peinture.
		Peintre peintre = new Peintre(
			new BufferedImage(largeurTex, hauteurTex, BufferedImage.TYPE_INT_ARGB),
			new BufferedImage(largeurTex, hauteurTex, BufferedImage.TYPE_INT_ARGB),
			new Random(graine), hauteurModele);
		for (Cube c : tous) {
			peindreCube(peintre, c);
		}
		ecrireImage(peintre.tex, "textures/entity/" + nom + ".png");
		ecrireImage(peintre.lueur, "textures/entity/" + nom + "_glowmask.png");

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

	// =================================================================================
	//  PEINTURE DES TEXTURES DE CRÉATURES
	// =================================================================================

	/**
	 * Tout ce dont la peinture a besoin.
	 * NOTION JAVA — "record" : petite classe immuable dont Java écrit lui-même le constructeur
	 * et les accesseurs (peintre.tex(), peintre.alea()...).
	 */
	record Peintre(BufferedImage tex, BufferedImage lueur, Random alea, double hauteurModele) {
	}

	enum Face { DESSUS, DESSOUS, COTE, AVANT, ARRIERE }

	/** Matrice de Bayer 4x4 : trame régulière qui mélange deux couleurs voisines (effet pixel art). */
	private static final int[][] BAYER = {{0, 8, 2, 10}, {12, 4, 14, 6}, {3, 11, 1, 9}, {15, 7, 13, 5}};

	/**
	 * Peint les 6 faces d'un cube dans sa zone "box UV" :
	 *   [  ][haut ][bas ][  ]
	 *   [côté][avant][côté][arrière]
	 */
	private static void peindreCube(Peintre p, Cube c) {
		int u = c.u, v = c.v, w = c.sx, h = c.sy, d = c.sz;
		peindreFace(p, c, Face.DESSUS, u + d, v, w, d);
		peindreFace(p, c, Face.DESSOUS, u + d + w, v, w, d);
		peindreFace(p, c, Face.COTE, u, v + d, d, h);
		peindreFace(p, c, Face.AVANT, u + d, v + d, w, h);
		peindreFace(p, c, Face.COTE, u + d + w, v + d, d, h);
		peindreFace(p, c, Face.ARRIERE, u + 2 * d + w, v + d, w, h);
	}

	private static void peindreFace(Peintre p, Cube c, Face face, int x0, int y0, int fw, int fh) {
		if (fw <= 0 || fh <= 0) return;
		Matiere m = c.matiere;
		boolean laterale = face == Face.AVANT || face == Face.ARRIERE || face == Face.COTE;

		for (int r = 0; r < fh; r++) {
			for (int x = 0; x < fw; x++) {
				int couleur;
				boolean brille;
				if (m == Matiere.BRAISE) {
					couleur = couleurBraise(x, r, fw, fh);
					brille = true;
				} else {
					// Hauteur réelle du pixel dans le modèle (0 = pieds, 1 = sommet).
					double yMonde = laterale ? c.oy + c.sy - (r + 0.5) : (face == Face.DESSUS ? c.oy + c.sy : c.oy);
					double lumiere = lumiereDeBase(face, laterale, x, r, fw, fh)
						+ (p.alea().nextDouble() - 0.5) * m.grain
						+ modulation(m, laterale, x, r, fw, fh, yMonde / p.hauteurModele(), c);
					int indice = indiceTrame(m.rampe.length, lumiere, x, r);
					couleur = m.rampe[indice];
					brille = m.brillance >= 0 && indice >= m.brillance;
				}
				poser(p.tex(), x0 + x, y0 + r, couleur);
				if (brille) poser(p.lueur(), x0 + x, y0 + r, couleur);
			}
		}
		details(p, c, face, laterale, x0, y0, fw, fh);
	}

	/** Éclairage venu d'en haut : dessus clair, dessous sombre, arêtes biseautées. */
	private static double lumiereDeBase(Face face, boolean laterale, int x, int r, int fw, int fh) {
		double l = switch (face) {
			case DESSUS -> 0.86;
			case AVANT -> 0.66;
			case COTE -> 0.56;
			case ARRIERE -> 0.48;
			case DESSOUS -> 0.26;
		};
		if (laterale && fh > 1) l += 0.16 * (0.5 - r / (double) (fh - 1));
		if (fw >= 3 && fh >= 3) {
			if (r == 0 || x == 0) l += 0.1;
			if (r == fh - 1 || x == fw - 1) l -= 0.1;
		}
		return l;
	}

	/** Motifs propres à chaque matière (plis, mailles, lignes de plaques...). */
	private static double modulation(Matiere m, boolean laterale, int x, int r, int fw, int fh, double hauteur, Cube c) {
		return switch (m) {
			case CENDRE, CHARBON, GRIFFE -> (hauteur - 0.55) * 0.3; // calciné en bas, plus pâle en haut
			case CAVITE -> 0.35 * (1.0 - Math.hypot((x - (fw - 1) / 2.0) / Math.max(1, fw / 2.0), (r - (fh - 1) / 2.0) / Math.max(1, fh / 2.0)));
			case CRISTAL -> ((x + r) % 4 == 0 ? 0.22 : 0.0) + (x == 0 ? 0.1 : 0.0);
			case ARMURE -> (laterale && fh >= 6) ? (r % 5 == 4 ? -0.22 : (r % 5 == 0 && r > 0 ? 0.08 : 0.0)) : 0.0;
			case OR -> ((fw >= 5 && fh >= 5 && (x == 1 || x == fw - 2 || r == 1 || r == fh - 2)) ? -0.22 : 0.0) + ((x + r) % 6 == 0 ? 0.12 : 0.0);
			case TISSU, TISSU_DECHIRE -> laterale ? 0.13 * Math.sin(x * 1.3 + c.ox) : 0.0;
			case LAME -> (x == 0 || x == fw - 1) ? 0.3 : ((laterale && fw >= 3 && x == fw / 2) ? -0.15 : 0.0);
			case CUIR -> (x + r) % 3 == 0 ? -0.18 : 0.0;
			case MAILLE -> (x % 2 == 0 && r % 2 == 0) ? -0.3 : ((x + r) % 2 == 1 ? 0.1 : 0.0);
			default -> 0.0;
		};
	}

	/** Choisit une couleur de la rampe ; la trame de Bayer adoucit le passage entre deux teintes. */
	private static int indiceTrame(int taille, double lumiere, int x, int r) {
		double valeur = Math.max(0.0, Math.min(1.0, lumiere)) * (taille - 1);
		int base = (int) Math.floor(valeur);
		double seuil = (BAYER[r & 3][x & 3] + 0.5) / 16.0;
		int indice = base + (valeur - base > seuil ? 1 : 0);
		return Math.max(0, Math.min(taille - 1, indice));
	}

	/** Braise incandescente : blanche au centre, jaune, orange puis rouge sur les bords. */
	private static int couleurBraise(int x, int r, int fw, int fh) {
		double cx = (fw - 1) / 2.0, cy = (fh - 1) / 2.0;
		double dist = Math.hypot((x - cx) / Math.max(1.0, fw / 2.0), (r - cy) / Math.max(1.0, fh / 2.0));
		if (dist < 0.35) return 0xFFFFF6D0;
		if (dist < 0.7) return 0xFFFFD040;
		if (dist < 1.0) return 0xFFFF8A1E;
		return 0xFFE0401A;
	}

	/** Détails ajoutés par-dessus la peinture de base. */
	private static void details(Peintre p, Cube c, Face face, boolean laterale, int x0, int y0, int fw, int fh) {
		int aire = fw * fh;
		switch (c.matiere) {
			case CENDRE -> veines(p, x0, y0, fw, fh, aire >= 6 ? Math.max(1, aire / 26) : 0, 0.55);
			case CHARBON -> braisesEparses(p, x0, y0, fw, fh, 0.035);
			case GRIFFE -> {
				braisesEparses(p, x0, y0, fw, fh, 0.03);
				if (laterale) { // pointes des griffes chauffées au rouge
					poser(p.tex(), x0, y0 + fh - 1, 0xFFFF6A1A);
					poser(p.lueur(), x0, y0 + fh - 1, 0xFFFF6A1A);
				}
			}
			case COTE -> veines(p, x0, y0, fw, fh, aire / 40, 0.0);
			case ARMURE -> {
				rayures(p, x0, y0, fw, fh, aire / 45);
				if ((face == Face.AVANT || face == Face.ARRIERE) && fw >= 6 && fh >= 5) {
					rivet(p, x0 + 1, y0 + 1);
					rivet(p, x0 + fw - 2, y0 + 1);
					if (fh >= 9) {
						rivet(p, x0 + 1, y0 + fh - 2);
						rivet(p, x0 + fw - 2, y0 + fh - 2);
					}
				}
				veines(p, x0, y0, fw, fh, aire / 90, 0.6); // la Cendre ronge l'armure
			}
			case TISSU, TISSU_DECHIRE -> {
				if ((face == Face.AVANT || face == Face.ARRIERE) && fw >= 5 && fh >= 10) {
					for (int r = 0; r < fh; r++) { // liseré doré
						int or = Matiere.OR.rampe[r % 3 == 0 ? 5 : 4];
						poser(p.tex(), x0, y0 + r, or);
						poser(p.tex(), x0 + fw - 1, y0 + r, or);
					}
				}
				if (c.matiere == Matiere.TISSU && face == Face.ARRIERE && fw >= 10 && fh >= 12) {
					embleme(p, x0 + (fw - 7) / 2, y0 + 3);
				}
				if (c.matiere == Matiere.TISSU_DECHIRE && laterale && fh >= 6) {
					dechirures(p, x0, y0, fw, fh);
				}
			}
			case LAME -> {
				if (laterale && face != Face.COTE && fw >= 3 && fh >= 6) { // runes gravées et ardentes
					int cx = x0 + fw / 2;
					for (int r = 1; r < fh - 1; r++) {
						if (r % 4 != 0) {
							int rune = r % 2 == 0 ? 0xFFFFC060 : 0xFFFF7A20;
							poser(p.tex(), cx, y0 + r, rune);
							poser(p.lueur(), cx, y0 + r, rune);
						}
					}
				}
			}
			default -> { }
		}
	}

	/** Fissures : certaines rougeoient de lave (et vont dans la glowmask), d'autres sont mortes. */
	private static void veines(Peintre p, int x0, int y0, int fw, int fh, int nombre, double probaArdente) {
		Random alea = p.alea();
		for (int n = 0; n < nombre; n++) {
			int x = alea.nextInt(fw), y = alea.nextInt(fh);
			boolean ardente = alea.nextDouble() < probaArdente;
			int longueur = 3 + alea.nextInt(4);
			for (int i = 0; i < longueur; i++) {
				if (x < 0 || y < 0 || x >= fw || y >= fh) break;
				if (ardente) {
					int couleur = i < 2 ? 0xFFFFD25A : (i < longueur - 1 ? 0xFFFF7A1A : 0xFFC8360E);
					poser(p.tex(), x0 + x, y0 + y, couleur);
					poser(p.lueur(), x0 + x, y0 + y, couleur);
					// Bord sombre à droite de la fissure : donne du relief.
					if (x + 1 < fw && (p.lueur().getRGB(x0 + x + 1, y0 + y) >>> 24) == 0) {
						poser(p.tex(), x0 + x + 1, y0 + y, eclaircir(p.tex().getRGB(x0 + x + 1, y0 + y), 0.6F));
					}
				} else {
					poser(p.tex(), x0 + x, y0 + y, 0xFF120E0D);
				}
				x += alea.nextInt(3) - 1;
				y += 1;
			}
		}
	}

	private static void braisesEparses(Peintre p, int x0, int y0, int fw, int fh, double proba) {
		for (int y = 0; y < fh; y++) {
			for (int x = 0; x < fw; x++) {
				if (p.alea().nextDouble() < proba) {
					int braise = p.alea().nextBoolean() ? 0xFFFF5A14 : 0xFFFFA040;
					poser(p.tex(), x0 + x, y0 + y, braise);
					poser(p.lueur(), x0 + x, y0 + y, braise);
				}
			}
		}
	}

	/** Éraflures claires sur le métal. */
	private static void rayures(Peintre p, int x0, int y0, int fw, int fh, int nombre) {
		for (int n = 0; n < nombre; n++) {
			int x = p.alea().nextInt(fw), y = p.alea().nextInt(fh);
			int longueur = 2 + p.alea().nextInt(2);
			for (int i = 0; i < longueur && x + i < fw && y + i < fh; i++) {
				poser(p.tex(), x0 + x + i, y0 + y + i, eclaircir(p.tex().getRGB(x0 + x + i, y0 + y + i), 1.6F));
			}
		}
	}

	private static void rivet(Peintre p, int x, int y) {
		poser(p.tex(), x, y, Matiere.OR.rampe[5]);
		poser(p.tex(), x + 1, y + 1, eclaircir(p.tex().getRGB(x + 1, y + 1), 0.6F));
	}

	/** Bas de tissu déchiré et brûlé : pixels transparents, bord calciné ou encore ardent. */
	private static void dechirures(Peintre p, int x0, int y0, int fw, int fh) {
		int profondeur = p.alea().nextInt(3);
		for (int x = 0; x < fw; x++) {
			profondeur = Math.max(0, Math.min(3, profondeur + p.alea().nextInt(3) - 1));
			for (int k = 0; k < profondeur; k++) {
				poser(p.tex(), x0 + x, y0 + fh - 1 - k, 0x00000000);
			}
			int bord = y0 + fh - 1 - profondeur;
			if (p.alea().nextDouble() < 0.45) {
				int braise = p.alea().nextBoolean() ? 0xFFFF6A1A : 0xFFFFB040;
				poser(p.tex(), x0 + x, bord, braise);
				poser(p.lueur(), x0 + x, bord, braise);
			} else {
				poser(p.tex(), x0 + x, bord, 0xFF1A0A08);
			}
		}
	}

	/** Sceau du dragon brodé en or au dos de la cape (7x7). */
	private static void embleme(Peintre p, int x0, int y0) {
		String[] motif = {
			"...#...",
			"..###..",
			".#.#.#.",
			"#..#..#",
			"..###..",
			".#...#.",
			"#.....#"
		};
		for (int y = 0; y < motif.length; y++) {
			for (int x = 0; x < motif[y].length(); x++) {
				if (motif[y].charAt(x) == '#') {
					poser(p.tex(), x0 + x, y0 + y, Matiere.OR.rampe[y < 3 ? 6 : 5]);
					poser(p.tex(), x0 + x, y0 + y + 1, eclaircir(p.tex().getRGB(x0 + x, y0 + y + 1), 0.7F));
				}
			}
		}
		// Le cœur du dragon, au centre, brille.
		poser(p.tex(), x0 + 3, y0 + 4, 0xFFFF8A1E);
		poser(p.lueur(), x0 + 3, y0 + 4, 0xFFFF8A1E);
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
				// La lame s'affine vers la pointe.
				double largeur = t > 0.8 ? 0.5 + 0.75 * (1.02 - t) / 0.22 : 1.25;
				if (dist < 0.55) {
					// Gouttière centrale : runes en segments, acier poli près de la pointe.
					boolean segment = t > 0.12 && t < 0.78 && ((int) (t * 16)) % 3 != 2;
					poser(img, x, y, segment ? rune : (t >= 0.78 ? 0xFFEEF3FA : 0xFF5A6272));
				} else if (dist < largeur) {
					// Tranchant clair en haut à gauche, ombre en bas à droite, reflet vers la pointe.
					poser(img, x, y, cote > 0 ? (t > 0.55 ? 0xFFFFFFFF : 0xFFD4DCE8) : (t > 0.55 ? 0xFF7A8496 : 0xFF464E5E));
				} else if (dist < largeur + 0.7 && t > 0.04) {
					poser(img, x, y, CONTOUR);
				}
			}
		}
		// Garde dorée en forme d'ailes, perpendiculaire à la lame.
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				double[] dt = distanceSegment(x + 0.5, y + 0.5, 1.8, 8.6, 7.6, 14.4);
				if (dt[1] >= 0 && dt[1] <= 1) {
					boolean bout = dt[1] < 0.12 || dt[1] > 0.88;
					if (dt[0] < 0.7) poser(img, x, y, dt[2] > 0 ? (bout ? 0xFFFFF0A0 : 0xFFF0C050) : 0xFF9A6418);
					else if (dt[0] < 1.4) poser(img, x, y, CONTOUR);
				}
			}
		}
		// Gemme de rune au centre de la garde (avec un reflet).
		poser(img, 4, 11, rune);
		poser(img, 5, 11, runeSombre);
		poser(img, 4, 12, runeSombre);
		poser(img, 5, 10, 0xFFFFFFFF);
		// Poignée de cuir tressé et pommeau en rubis.
		poser(img, 2, 12, 0xFF3A1E12);
		poser(img, 3, 12, 0xFF7A4A2C);
		poser(img, 2, 13, 0xFF7A4A2C);
		poser(img, 3, 13, 0xFF3A1E12);
		poser(img, 1, 14, 0xFFE03040);
		poser(img, 0, 15, 0xFF701018);
		poser(img, 1, 15, 0xFF901820);
		poser(img, 0, 14, CONTOUR);
		poser(img, 2, 14, CONTOUR);
		poser(img, 2, 15, CONTOUR);
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
