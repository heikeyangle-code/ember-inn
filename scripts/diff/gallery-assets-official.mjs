#!/usr/bin/env node
// Gallery 排序字面值 + Assets 类型集 差分（5 例）。
// - Gallery 4 种排序：nameAsc / nameDesc / dateDesc / dateAsc（index.js:63-68）
// - Assets 5 种类型：extension / character / ambient / bgm / blip（index.js:55-61）
import { writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const OUT = join(__dirname, '../../engine/src/test/resources/diff/gallery-assets.json');

function main(){
    const cases = [];
    let id = 0;
    cases.push({id:id++,name:'gal-sort-nameAsc',_tag:'gallery-sort',expected:{value:'nameAsc',field:'name',order:'asc'}});
    cases.push({id:id++,name:'gal-sort-nameDesc',_tag:'gallery-sort',expected:{value:'nameDesc',field:'name',order:'desc'}});
    cases.push({id:id++,name:'gal-sort-dateDesc',_tag:'gallery-sort',expected:{value:'dateDesc',field:'date',order:'desc'}});
    cases.push({id:id++,name:'gal-sort-dateAsc',_tag:'gallery-sort',expected:{value:'dateAsc',field:'date',order:'asc'}});
    cases.push({id:id++,name:'ass-types-5',_tag:'assets-types',expected:{types:['extension','character','ambient','bgm','blip']}});
    const fixture = { generatedAt: new Date().toISOString(),
        source: 'gallery/index.js SORT + assets/index.js KNOWN_TYPES',
        cases };
    writeFileSync(OUT, JSON.stringify(fixture, null, 2));
    console.log('gallery/assets fixtures:', cases.length, '→', OUT);
}
main();
