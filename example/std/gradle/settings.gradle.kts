
for (module in _project.modules) {
    if (module.path.isNotEmpty()) {
        include(":${module.path.replace('/', ':')}")
    }
}