const fs=require("fs");
function edit(f, pairs){let s=fs.readFileSync(f,"utf8");for(const [a,b] of pairs){if(!s.includes(a))throw new Error(f+": "+a);s=s.replace(a,b);}fs.writeFileSync(f,s);}
edit(".github/workflows/play-promote.yml",[
[`          if [ "$ROLLOUT" = "1" ] || [[ "$ROLLOUT" =~ ^1\.0+$ ]]; then STATUS=completed; else STATUS=inProgress; fi
          ARGS="--track $FROM --rollout $ROLLOUT --release_status $STATUS --skip`,`          # A rollout below 1 is a staged release; 1 completes it.
          if [ "$ROLLOUT" = "1" ] || [[ "$ROLLOUT" =~ ^1\.0+$ ]]; then STAGE="--release_status completed"
          else STAGE="--release_status inProgress --rollout $ROLLOUT"; fi
          ARGS="--track $FROM $STAGE --skip`],
]);
edit(".github/workflows/release.yml",[
[`# \`testers\`). Builds of main`,`# \`testers\`) and, once Google Play is set up, the AAB to the Play track in the PLAY_TRACK variable
# (docs/play/GUIA-PLAY-CONSOLE.md; play-promote.yml moves it on). Builds of main`],
[`      - name: Remove credentials from the runner`,`      # Release notes per language: the version and a link to the GitHub notes (Play allows 500
      # characters). Nothing happens until PLAY_TRACK is set and the key is in Doppler.
      - name: Play release notes
        if: vars.PLAY_TRACK != ''
        env:
          VERSION: \${{ steps.ver.outputs.version }}
          TAG: \${{ steps.ver.outputs.tag }}
        run: |
          CODE=$(git rev-list --count HEAD)
          NOTES="https://github.com/$GITHUB_REPOSITORY/releases/tag/$TAG"
          for LOCALE in es-ES en-US; do mkdir -p "fastlane/metadata/android/$LOCALE/changelogs"; done
          printf "Owlsplit %s: mejoras y correcciones.\nNovedades: %s" "$VERSION" "$NOTES" > "fastlane/metadata/android/es-ES/changelogs/$CODE.txt"
          printf "Owlsplit %s: improvements and fixes.\nWhat's new: %s" "$VERSION" "$NOTES" > "fastlane/metadata/android/en-US/changelogs/$CODE.txt"

      - name: Upload to Google Play
        if: vars.PLAY_TRACK != ''
        uses: ./.github/actions/play
        with:
          args: >-
            --aab \${{ steps.assets.outputs.aab }} --track \${{ vars.PLAY_TRACK }} --release_status completed
            --metadata_path fastlane/metadata/android --skip_upload_metadata true --skip_upload_images true
            --skip_upload_screenshots true --mapping composeApp/build/outputs/mapping/release/mapping.txt

      - name: Remove credentials from the runner`],
]);
