
scut.hotkey("slide_in", function()
    local transform = scut.get_node("transform")
    scut.animate(transform, "x", -1.0, 0.0, 0.45)
end)
