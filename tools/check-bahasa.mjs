// Pemeriksa kualitas Bahasa Indonesia untuk proyek ini.
//
// Salinan dari salinan global di ~/.config/opencode/tools. Versi di repo
// dipakai oleh alur kerja otomatis dan oleh hook commit, supaya hasilnya
// sama di mana pun pemeriksaan dilakukan.
//
// Rujukan lengkap ada di docs/libraries.md bagian perkakas.

import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { createRequire } from 'node:module';

const require = createRequire(import.meta.url);
const HERE = path.dirname(fileURLToPath(import.meta.url));

const DICT_AFF = path.join(HERE, 'dictionary-id', 'id_ID.aff');
const DICT_DIC = path.join(HERE, 'dictionary-id', 'id_ID.dic');
const LIST_TEKNIS = path.join(HERE, 'kata-teknis.txt');
const LIST_SALAH = path.join(HERE, 'kata-salah.txt');
const LIST_FRASA = path.join(HERE, 'frasa-izin.txt');

const AKASRA_LARANG = [
  { nama: 'Mandarin/Han', rentang: [0x4e00, 0x9fff] },
  { nama: 'Tanda baca CJK', rentang: [0x3000, 0x303f] },
  { nama: 'Fullwidth', rentang: [0xff00, 0xffef] },
  { nama: 'Hiragana/Katakana', rentang: [0x3040, 0x30ff] },
  { nama: 'Hangul', rentang: [0xac00, 0xd7af] },
  { nama: 'Cyrillic', rentang: [0x0400, 0x04ff] },
  { nama: 'Arab/Ibrani', rentang: [0x0600, 0x06ff] },
  { nama: 'Thai', rentang: [0x0e00, 0x0e7f] }
];

const KATA_FUNGSI_INGGRIS = new Set([
  'the', 'and', 'with', 'from', 'this', 'that', 'when', 'then', 'than',
  'have', 'has', 'been', 'were', 'they', 'them', 'their', 'there',
  'which', 'while', 'would', 'could', 'should', 'about', 'into', 'over',
  'under', 'after', 'before', 'between', 'through', 'during', 'without',
  'these', 'those', 'such', 'both', 'each', 'other', 'only'
]);

const FRASA_IZIN_BAWASAN = [
  'identity and access management',
  'secrets and variables',
  'run with',
  'spring boot',
  'spring data jpa',
  'spring security',
  'oauth2 resource server',
  'data jpa test',
  'webmvc test',
  'open api',
  'jwt bearer',
  'basic auth',
  'api key',
  'lgpl-3.0-only'
];

/** Daftar frasa yang diizinkan, dapat ditambah dari berkas. */
let frasaIzin = new Set(FRASA_IZIN_BAWASAN);

function bacaDaftar(file) {
  if (!fs.existsSync(file)) return new Set();
  return new Set(
    fs.readFileSync(file, 'utf8')
      .split('\n')
      .map((baris) => baris.trim().toLowerCase())
      .filter((baris) => baris && !baris.startsWith('#'))
  );
}

function muatKamus() {
  if (!fs.existsSync(DICT_AFF) || !fs.existsSync(DICT_DIC)) return null;
  try {
    const nspell = require('nspell');
    return nspell(
      fs.readFileSync(DICT_AFF, 'utf8'),
      fs.readFileSync(DICT_DIC, 'utf8')
    );
  } catch (ex) {
    // Pustaka nspell belum terpasang. Kamus hanya dipakai pada mode ketat,
    // jadi mode bawaan tetap berfungsi.
    return null;
  }
}

/**
 * Menghapus bagian yang tidak perlu diperiksa: blok kode, kode sebaris,
 * tautan markdown, URL, teks dalam tanda kutip, dan frasa yang diizinkan.
 */
function bersihkan(teks) {
  let hasil = teks
    .replace(/```[\s\S]*?```/g, (b) => b.replace(/[^\n]/g, ' '))
    .replace(/`[^`\n]*`/g, (b) => b.replace(/[^\n]/g, ' '))
    .replace(/!?\[([^\]]*)\]\([^)]*\)/g, '$1')
    .replace(/<[^>\n]+>/g, ' ')
    .replace(/https?:\/\/\S+/gi, ' ');

  // Teks di dalam tanda kutip adalah kutipan asli dari sumber lain, misalnya
  // pesan galat browser atau label antarmuka. Isinya tidak boleh dievaluasi.
  // Polanya boleh melewati baris baru, karena kutipan sering dibuka di satu
  // baris dan ditutup di baris berikutnya.
  hasil = hasil.replace(/"[^"]*"/g, (b) => b.replace(/[^\n]/g, ' '));
  hasil = hasil.replace(/'[^']*'/g, (b) => b.replace(/[^\n]/g, ' '));

  // Frasa yang diizinkan dibiarkan utuh, tapi ditandai agar tidak dibaca
  // sebagai kata terpisah.
  for (const frasa of frasaIzin) {
    const pola = new RegExp(frasa.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'), 'gi');
    hasil = hasil.replace(pola, (b) => b.replace(/[^\n]/g, ' '));
  }

  return hasil;
}

/** Pola kata yang tidak diperiksa karena bukan kata biasa. */
function bukanKata(nilai) {
  return (
    /^[A-Z]/.test(nilai) ||
    /-/.test(nilai) ||
    /^\d+$/.test(nilai) ||
    /^[^\p{L}]+$/u.test(nilai) ||
    /[a-z][A-Z]/.test(nilai)
  );
}

function periksaAksara(teks, temuan) {
  const baris = teks.split('\n');
  baris.forEach((isiBaris, nomor) => {
    for (const char of isiBaris) {
      const kode = char.codePointAt(0);
      const aturan = AKASRA_LARANG.find(
        (a) => kode >= a.rentang[0] && kode <= a.rentang[1]
      );
      if (aturan) {
        temuan.push({
          jenis: 'aksara',
          tingkat: 'galat',
          pesan: `karakter ${aturan.nama} (U+${kode.toString(16).toUpperCase()})`,
          baris: nomor + 1
        });
        break;
      }
    }
  });
}

function periksaDaftar(salah, teks, temuan) {
  const bersih = bersihkan(teks);

  for (const frasa of salah) {
    const pola = new RegExp(
      `(^|[^\\p{L}])${frasa.replace(/[.*+?^${}()|[\\]\\\\]/g, '\\\\$&')}([^\\p{L}]|$)`,
      'giu'
    );
    if (pola.test(bersih)) {
      temuan.push({
        jenis: 'daftar',
        tingkat: 'galat',
        pesan: `kata "${frasa}" pernah dipakai dengan salah`
      });
    }
  }
}

function periksaCampuran(teks, temuan) {
  const baris = bersihkan(teks).split('\n');
  baris.forEach((isiBaris, nomor) => {
    const kata = isiBaris.match(/[\p{L}][\p{L}'-]*/gu) || [];
    for (const nilai of kata) {
      const kecil = nilai.toLowerCase();
      if (KATA_FUNGSI_INGGRIS.has(kecil) && !bukanKata(nilai)) {
        temuan.push({
          jenis: 'campuran',
          tingkat: 'perhatian',
          pesan: `kata fungsi bahasa Inggris "${nilai}" di dalam prosa Indonesia`,
          baris: nomor + 1
        });
      }
    }
  });
}

function periksaGanda(teks, temuan) {
  const baris = bersihkan(teks).split('\n');
  baris.forEach((isiBaris, nomor) => {
    // Pengulangan hanya dianggap salah kalau kedua kata benar-benar
    // berdampingan. Dalam Bahasa Indonesia pengulangan kata di akhir dan
    // awal klausa itu sah, misalnya "token refresh, token lama".
    const pola = /(\b[\p{L}]{3,})(\s+\1\b)/giu;
    let cocok;
    while ((cocok = pola.exec(isiBaris)) !== null) {
      temuan.push({
        jenis: 'ganda',
        tingkat: 'perhatian',
        pesan: `kata "${cocok[1]}" ditulis dua kali berturut-turut`,
        baris: nomor + 1
      });
    }
  });
}

function periksaEjaan(kamus, teknis, teks, temuan) {
  if (!kamus) return;
  const baris = bersihkan(teks).split('\n');

  baris.forEach((isiBaris, nomor) => {
    const kata = isiBaris.match(/[\p{L}][\p{L}'-]*/gu) || [];
    for (const nilai of kata) {
      if (bukanKata(nilai)) continue;
      if (teknis.has(nilai.toLowerCase())) continue;
      if (kamus.correct(nilai)) continue;
      temuan.push({
        jenis: 'ejaan',
        tingkat: 'perhatian',
        pesan: `"${nilai}" tidak dikenal sebagai kata Indonesia`,
        baris: nomor + 1
      });
    }
  });
}

function periksa(teks, opsi) {
  const temuan = [];
  const kamus = opsi.ketat ? muatKamus() : null;
  const teknis = bacaDaftar(LIST_TEKNIS);
  const salah = bacaDaftar(LIST_SALAH);

  periksaAksara(teks, temuan);
  periksaDaftar(salah, teks, temuan);
  periksaCampuran(teks, temuan);
  periksaGanda(teks, temuan);
  if (opsi.ketat) periksaEjaan(kamus, teknis, teks, temuan);

  return temuan;
}

function jalankan() {
  const argumen = process.argv.slice(2);
  const opsi = {
    stdin: argumen.includes('--stdin'),
    ketat: argumen.includes('--ketat')
  };
  const berkas = argumen.filter((a) => !a.startsWith('--'));

  for (const frasa of bacaDaftar(LIST_FRASA)) {
    frasaIzin.add(frasa);
  }

  const hasil = [];

  if (opsi.stdin) {
    const teks = fs.readFileSync(0, 'utf8');
    hasil.push({ nama: 'stdin', temuan: periksa(teks, opsi) });
  } else {
    for (const nama of berkas) {
      if (!fs.existsSync(nama)) {
        console.log(`⚠️  Berkas tidak ditemukan: ${nama}`);
        continue;
      }
      const teks = fs.readFileSync(nama, 'utf8');
      hasil.push({ nama, temuan: periksa(teks, opsi) });
    }
  }

  let adaGalat = false;

  for (const { nama, temuan } of hasil) {
    if (temuan.length === 0) {
      console.log(`✅ ${nama}`);
      continue;
    }

    const galat = temuan.filter((t) => t.tingkat === 'galat');
    const perhatian = temuan.filter((t) => t.tingkat === 'perhatian');

    if (galat.length > 0) adaGalat = true;

    console.log(`${galat.length > 0 ? '🚨' : '⚠️ '} ${nama}`);
    if (galat.length > 0) console.log(`   ${galat.length} galat`);
    if (perhatian.length > 0) console.log(`   ${perhatian.length} perhatian`);

    const batasi = 30;
    temuan.slice(0, batasi).forEach((t) => {
      const posisi = t.baris ? ` (baris ${t.baris})` : '';
      console.log(`   - [${t.jenis}] ${t.pesan}${posisi}`);
    });

    if (temuan.length > batasi) {
      console.log(`   ... dan ${temuan.length - batasi} temuan lain`);
    }
  }

  process.exit(adaGalat ? 1 : 0);
}

jalankan();
