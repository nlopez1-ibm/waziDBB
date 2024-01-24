/* gitPrune    - delete all commits up to the current head */
WIP dont run 

git checkout --orphan main
git add -A
git commit -am "Initial commit message"
git branch -D main
git branch -m master  
git push -f origin master to master branch.
git gc --aggressive --prune=all # remove the old files.