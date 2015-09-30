
def stagedRepo = []

stage 'one'
test1 = tester {
  isRelease = 'true'
  name = 'james'
  version = '1.1.1'
  repoId = '11'
}


stage 'two'
test2 = tester {
  isRelease = 'true'
  name = 'kath'
  version = '1.1.2'
  repoId = '12'
}

stage 'three'
test3 = tester {
  isRelease = 'true'
  name = 'poppy'
  version = '1.1.3'
  repoId = '13'
}

stage 'four'
test4 = tester {
  isRelease = 'true'
  name = 'felix'
  version = '1.1.4'
  repoId = '14'
}

stagedRepo << test1 << test2 << test3 << test4

echo 'START'
echo stagedRepo[0].name
echo stagedRepo[0].version
echo stagedRepo[0].repoId

echo stagedRepo[1].name
echo stagedRepo[1].version
echo stagedRepo[1].repoId

echo stagedRepo[2].name
echo stagedRepo[2].version
echo stagedRepo[2].repoId

stagedRepo.each {
    echo "Item: $it.name"
}
echo 'FINISHED'

def list = []

list << 7 << 'i' << 11

list.each {
    echo "Item2: $it"
}


for(int i = 0; i < list.size(); i++){
  def item = list[i]
  echo "$item"
}
