# -*- Mode: JavaScript; tab-width: 2; indent-tabs-mode: nil; -*-
# vim:set ft=javascript ts=2 sw=2 sts=2 cindent:
Visualizer = (($, window, undefined_) ->
  fontLoadTimeout = 1 #ms ;; 5000; // 5 seconds
  DocumentData = (text) ->
    @text = text
    @chunks = []
    @spans = {}
    @eventDescs = {}
    @sentComment = {}
    @arcs = []
    @arcById = {}
    @markedSent = {}
    @spanAnnTexts = {}
    @towers = {}

  
  # this.sizes = {};
  Fragment = (id, span, from, to) ->
    @id = id
    @span = span
    @from = from
    @to = to

  
  # this.towerId = undefined;
  # this.drawOrder = undefined;
  Span = (id, type, offsets, generalType) ->
    @id = id
    @type = type
    @totalDist = 0
    @numArcs = 0
    @generalType = generalType
    @headFragment = null
    @unsegmentedOffsets = offsets
    @offsets = []
    @segmentedOffsetsMap = {}
    
    # this.unsegmentedOffsets = undefined;
    # this.from = undefined;
    # this.to = undefined;
    # this.wholeFrom = undefined;
    # this.wholeTo = undefined;
    # this.headFragment = undefined;
    # this.chunk = undefined;
    # this.marked = undefined;
    # this.avgDist = undefined;
    # this.curly = undefined;
    # this.comment = undefined; // { type: undefined, text: undefined };
    # this.annotatorNotes = undefined;
    # this.drawCurly = undefined;
    # this.glyphedLabelText = undefined;
    # this.group = undefined;
    # this.height = undefined;
    # this.highlightPos = undefined;
    # this.indexNumber = undefined;
    # this.labelText = undefined;
    # this.nestingDepth = undefined;
    # this.nestingDepthLR = undefined;
    # this.nestingDepthRL = undefined;
    # this.nestingHeight = undefined;
    # this.nestingHeightLR = undefined;
    # this.nestingHeightRL = undefined;
    # this.rect = undefined;
    # this.rectBox = undefined;
    # this.refedIndexSum = undefined;
    # this.right = undefined;
    # this.totaldist = undefined;
    # this.width = undefined;
    @initContainers()

  Span::initContainers = (offsets) ->
    @incoming = []
    @outgoing = []
    @attributes = {}
    @attributeText = []
    @attributeCues = {}
    @attributeCueFor = {}
    @attributeMerge = {} # for box, cross, etc. that are span-global
    @fragments = []
    @normalizations = []

  Span::splitMultilineOffsets = (text) ->
    @segmentedOffsetsMap = {}
    fi = 0
    nfi = 0

    while fi < @unsegmentedOffsets.length
      begin = @unsegmentedOffsets[fi][0]
      end = @unsegmentedOffsets[fi][1]
      ti = begin

      while ti < end
        c = text.charAt(ti)
        if c is "\n" or c is "\r"
          if begin isnt null
            @offsets.push [begin, ti]
            @segmentedOffsetsMap[nfi++] = fi
            begin = null
        else begin = ti  if begin is null
        ti++
      if begin isnt null
        @offsets.push [begin, end]
        @segmentedOffsetsMap[nfi++] = fi
      fi++

  Span::copy = (id) ->
    span = $.extend(new Span(), this) # clone
    span.id = id
    
    # protect from shallow copy
    span.initContainers()
    span.unsegmentedOffsets = @unsegmentedOffsets.slice()
    
    # read-only; shallow copy is fine
    span.offsets = @offsets
    span.segmentedOffsetsMap = @segmentedOffsetsMap
    span

  EventDesc = (id, triggerId, roles, klass) ->
    @id = id
    @triggerId = triggerId
    roleList = @roles = []
    $.each roles, (roleNo, role) ->
      roleList.push
        type: role[0]
        targetId: role[1]


    if klass is "equiv"
      @equiv = true
    else @relation = true  if klass is "relation"

  
  # this.leftSpans = undefined;
  # this.rightSpans = undefined;
  # this.annotatorNotes = undefined;
  Chunk = (index, text, from, to, space, spans) ->
    @index = index
    @text = text
    @from = from
    @to = to
    @space = space
    @fragments = []

  
  # this.sentence = undefined;
  # this.group = undefined;
  # this.highlightGroup = undefined;
  # this.markedTextStart = undefined;
  # this.markedTextEnd = undefined;
  # this.nextSpace = undefined;
  # this.right = undefined;
  # this.row = undefined;
  # this.textX = undefined;
  # this.translation = undefined;
  Arc = (eventDesc, role, dist, eventNo) ->
    @origin = eventDesc.id
    @target = role.targetId
    @dist = dist
    @type = role.type
    @shadowClass = eventDesc.shadowClass
    @jumpHeight = 0
    if eventDesc.equiv
      @equiv = true
      @eventDescId = eventNo
      eventDesc.equivArc = this
    else if eventDesc.relation
      @relation = true
      @eventDescId = eventNo

  
  # this.marked = undefined;
  Row = (svg) ->
    @group = svg.group()
    @background = svg.group(@group)
    @chunks = []
    @hasAnnotations = false
    @maxArcHeight = 0
    @maxSpanHeight = 0

  Measurements = (widths, height, y) ->
    @widths = widths
    @height = height
    @y = y

  
  # A naive whitespace tokeniser
  tokenise = (text) ->
    tokenOffsets = []
    tokenStart = null
    lastCharPos = null
    i = 0

    while i < text.length
      c = text[i]
      
      # Have we found the start of a token?
      if not tokenStart? and not /\s/.test(c)
        tokenStart = i
        lastCharPos = i
      
      # Have we found the end of a token?
      else if /\s/.test(c) and tokenStart?
        tokenOffsets.push [tokenStart, i]
        tokenStart = null
      
      # Is it a non-whitespace character?
      else lastCharPos = i  unless /\s/.test(c)
      i++
    
    # Do we have a trailing token?
    tokenOffsets.push [tokenStart, lastCharPos + 1]  if tokenStart?
    tokenOffsets

  
  # A naive newline sentence splitter
  sentenceSplit = (text) ->
    sentenceOffsets = []
    sentStart = null
    lastCharPos = null
    i = 0

    while i < text.length
      c = text[i]
      
      # Have we found the start of a sentence?
      if not sentStart? and not /\s/.test(c)
        sentStart = i
        lastCharPos = i
      
      # Have we found the end of a sentence?
      else if c is "\n" and sentStart?
        sentenceOffsets.push [sentStart, i]
        sentStart = null
      
      # Is it a non-whitespace character?
      else lastCharPos = i  unless /\s/.test(c)
      i++
    
    # Do we have a trailing sentence without a closing newline?
    sentenceOffsets.push [sentStart, lastCharPos + 1]  if sentStart?
    sentenceOffsets

  
  # Sets default values for a wide range of optional attributes
  setSourceDataDefaults = (sourceData) ->
    
    # The following are empty lists if not set
    $.each ["attributes", "comments", "entities", "equivs", "events", "modifications", "normalizations", "relations", "triggers"], (attrNo, attr) ->
      sourceData[attr] = []  if sourceData[attr] is `undefined`

    
    # If we lack sentence offsets we fall back on naive sentence splitting
    sourceData.sentence_offsets = sentenceSplit(sourceData.text)  if sourceData.sentence_offsets is `undefined`
    
    # Similarily we fall back on whitespace tokenisation
    sourceData.token_offsets = tokenise(sourceData.text)  if sourceData.token_offsets is `undefined`

  
  # Set default values for a variety of collection attributes
  setCollectionDefaults = (collectionData) ->
    
    # The following are empty lists if not set
    $.each ["entity_attribute_types", "entity_types", "event_attribute_types", "event_types", "relation_attribute_types", "relation_types", "unconfigured_types"], (attrNo, attr) ->
      collectionData[attr] = []  if collectionData[attr] is `undefined`


  Visualizer = (dispatcher, svgId, webFontURLs) ->
    $svgDiv = $("#" + svgId)
    throw Error("Could not find container with id=\"" + svgId + "\"")  unless $svgDiv.length
    that = this
    
    # OPTIONS
    roundCoordinates = true # try to have exact pixel offsets
    boxTextMargin = # effect is inverse of "margin" for some reason
      x: 0
      y: 1.5

    highlightRounding = # rx, ry for highlight boxes
      x: 3
      y: 3

    spaceWidths =
      " ": 4
      " ": 4
      "​": 0
      "　": 8
      "\n": 4

    coloredCurlies = true # color curlies by box BG
    arcSlant = 15 #10;
    minArcSlant = 8
    arcHorizontalSpacing = 10 # min space boxes with connecting arc
    rowSpacing = -5 # for some funny reason approx. -10 gives "tight" packing.
    sentNumMargin = 20
    smoothArcCurves = true # whether to use curves (vs lines) in arcs
    smoothArcSteepness = 0.5 # steepness of smooth curves (control point)
    reverseArcControlx = 5 # control point distance for "UFO catchers"
    
    # "shadow" effect settings (note, error, incompelete)
    rectShadowSize = 3
    rectShadowRounding = 2.5
    arcLabelShadowSize = 1
    arcLabelShadowRounding = 5
    shadowStroke = 2.5 # TODO XXX: this doesn't affect anything..?
    
    # "marked" effect settings (edited, focus, match)
    markedSpanSize = 6
    markedArcSize = 2
    markedArcStroke = 7 # TODO XXX: this doesn't seem to do anything..?
    rowPadding = 2
    nestingAdjustYStepSize = 2 # size of height adjust for nested/nesting spans
    nestingAdjustXStepSize = 1 # size of height adjust for nested/nesting spans
    highlightSequence = "#FF9632;#FFCC00;#FF9632" # yellow - deep orange
    #var highlightSequence = '#FFFC69;#FFCC00;#FFFC69'; // a bit toned town
    highlightSpanSequence = highlightSequence
    highlightArcSequence = highlightSequence
    highlightTextSequence = highlightSequence
    highlightDuration = "2s"
    
    # different sequence for "mere" matches (as opposed to "focus" and
    # "edited" highlights)
    highlightMatchSequence = "#FFFF00" # plain yellow
    fragmentConnectorDashArray = "1,3,3,3"
    fragmentConnectorColor = "#000000"
    
    # END OPTIONS
    svg = undefined
    $svg = undefined
    data = null
    sourceData = null
    requestedData = null
    coll = undefined
    doc = undefined
    args = undefined
    relationTypesHash = undefined
    isRenderRequested = undefined
    isCollectionLoaded = false
    entityAttributeTypes = null
    eventAttributeTypes = null
    spanTypes = null
    highlightGroup = undefined
    collapseArcs = true
    collapseArcSpace = false
    
    # var commentPrioLevels = ['Unconfirmed', 'Incomplete', 'Warning', 'Error', 'AnnotatorNotes'];
    # XXX Might need to be tweaked - inserted diff levels
    commentPrioLevels = ["Unconfirmed", "Incomplete", "Warning", "Error", "AnnotatorNotes", "AddedAnnotation", "MissingAnnotation", "ChangedAnnotation"]
    @arcDragOrigin = null # TODO
    
    # due to silly Chrome bug, I have to make it pay attention
    forceRedraw = ->
      return  unless $.browser.chrome # not needed
      $svg.css "margin-bottom", 1
      setTimeout (->
        $svg.css "margin-bottom", 0
      ), 0

    rowBBox = (span) ->
      box = $.extend({}, span.rectBox) # clone
      chunkTranslation = span.chunk.translation
      box.x += chunkTranslation.x
      box.y += chunkTranslation.y
      box

    commentPriority = (commentClass) ->
      return -1  if commentClass is `undefined`
      len = commentPrioLevels.length
      i = 0

      while i < len
        return i  unless commentClass.indexOf(commentPrioLevels[i]) is -1
        i++
      0

    clearSVG = ->
      data = null
      sourceData = null
      svg.clear()
      $svgDiv.hide()

    setMarked = (markedType) ->
      $.each args[markedType] or [], (markedNo, marked) ->
        if marked[0] is "sent"
          data.markedSent[marked[1]] = true
        else if marked[0] is "equiv" # [equiv, Equiv, T1]
          $.each sourceData.equivs, (equivNo, equiv) ->
            if equiv[1] is marked[1]
              len = equiv.length
              i = 2

              while i < len
                if equiv[i] is marked[2]
                  
                  # found it
                  len -= 3
                  i = 1

                  while i <= len
                    arc = data.eventDescs[equiv[0] + "*" + i].equivArc
                    arc.marked = markedType
                    i++
                  return # next equiv
                i++

        else if marked.length is 2
          markedText.push [parseInt(marked[0], 10), parseInt(marked[1], 10), markedType]
        else
          span = data.spans[marked[0]]
          if span
            if marked.length is 3 # arc
              $.each span.outgoing, (arcNo, arc) ->
                arc.marked = markedType  if arc.target is marked[2] and arc.type is marked[1]

            else # span
              span.marked = markedType
          else
            eventDesc = data.eventDescs[marked[0]]
            if eventDesc # relation
              relArc = eventDesc.roles[0]
              $.each data.spans[eventDesc.triggerId].outgoing, (arcNo, arc) ->
                arc.marked = markedType  if arc.target is relArc.targetId and arc.type is relArc.type

            else # try for trigger
              $.each data.eventDescs, (eventDescNo, eventDesc) ->
                data.spans[eventDesc.id].marked = markedType  if eventDesc.triggerId is marked[0]



    findArcHeight = (fromIndex, toIndex, fragmentHeights) ->
      height = 0
      i = fromIndex

      while i <= toIndex
        height = fragmentHeights[i]  if fragmentHeights[i] > height
        i++
      height += Configuration.visual.arcSpacing
      height

    adjustFragmentHeights = (fromIndex, toIndex, fragmentHeights, height) ->
      i = fromIndex

      while i <= toIndex
        fragmentHeights[i] = height  if fragmentHeights[i] < height
        i++

    fragmentComparator = (a, b) ->
      tmp = undefined
      aSpan = a.span
      bSpan = b.span
      
      # spans with more fragments go first
      tmp = aSpan.fragments.length - bSpan.fragments.length
      return (if tmp < 0 then 1 else -1)  if tmp
      
      # longer arc distances go last
      tmp = aSpan.avgDist - bSpan.avgDist
      return (if tmp < 0 then -1 else 1)  if tmp
      
      # spans with more arcs go last
      tmp = aSpan.numArcs - bSpan.numArcs
      return (if tmp < 0 then -1 else 1)  if tmp
      
      # compare the span widths,
      # put wider on bottom so they don't mess with arcs, or shorter
      # on bottom if there are no arcs.
      ad = a.to - a.from
      bd = b.to - b.from
      tmp = ad - bd
      tmp = -tmp  if aSpan.numArcs is 0 and bSpan.numArcs is 0
      return (if tmp < 0 then 1 else -1)  if tmp
      tmp = aSpan.refedIndexSum - bSpan.refedIndexSum
      return (if tmp < 0 then -1 else 1)  if tmp
      
      # if no other criterion is found, sort by type to maintain
      # consistency
      # TODO: isn't there a cmp() in JS?
      if aSpan.type < bSpan.type
        return -1
      else return 1  if aSpan.type > bSpan.type
      0

    setData = (_sourceData) ->
      args = {}  unless args
      sourceData = _sourceData
      dispatcher.post "newSourceData", [sourceData]
      data = new DocumentData(sourceData.text)
      
      # collect annotation data
      $.each sourceData.entities, (entityNo, entity) ->
        
        # offsets given as array of (start, end) pairs
        
        #      (id,        type,      offsets,   generalType)
        span = new Span(entity[0], entity[1], entity[2], "entity")
        span.splitMultilineOffsets data.text
        data.spans[entity[0]] = span

      triggerHash = {}
      $.each sourceData.triggers, (triggerNo, trigger) ->
        
        #       (id,         type,       offsets,    generalType)
        triggerSpan = new Span(trigger[0], trigger[1], trigger[2], "trigger")
        triggerSpan.splitMultilineOffsets data.text
        triggerHash[trigger[0]] = [triggerSpan, []] # triggerSpan, eventlist

      $.each sourceData.events, (eventNo, eventRow) ->
        
        #           (id,          triggerId,   roles,        klass)
        eventDesc = data.eventDescs[eventRow[0]] = new EventDesc(eventRow[0], eventRow[1], eventRow[2])
        trigger = triggerHash[eventDesc.triggerId]
        span = trigger[0].copy(eventDesc.id)
        trigger[1].push span
        data.spans[eventDesc.id] = span

      
      # XXX modifications: delete later
      $.each sourceData.modifications, (modNo, mod) ->
        
        # mod: [id, spanId, modification]
        unless data.spans[mod[2]]
          dispatcher.post "messages", [[["<strong>ERROR</strong><br/>Event " + mod[2] + " (referenced from modification " + mod[0] + ") does not occur in document " + data.document + "<br/>(please correct the source data)", "error", 5]]]
          return
        data.spans[mod[2]][mod[1]] = true

      midpointComparator = (a, b) ->
        tmp = a.from + a.to - b.from - b.to
        return 0  unless tmp
        (if tmp < 0 then -1 else 1)

      
      # split spans into span fragments (for discontinuous spans)
      $.each data.spans, (spanNo, span) ->
        $.each span.offsets, (offsetsNo, offsets) ->
          from = parseInt(offsets[0], 10)
          to = parseInt(offsets[1], 10)
          fragment = new Fragment(offsetsNo, span, from, to)
          span.fragments.push fragment

        
        # ensure ascending order
        span.fragments.sort midpointComparator
        span.wholeFrom = span.fragments[0].from
        span.wholeTo = span.fragments[span.fragments.length - 1].to
        span.headFragment = span.fragments[(if (true) then span.fragments.length - 1 else 0)] # TODO configurable!

      spanComparator = (a, b) ->
        aSpan = data.spans[a]
        bSpan = data.spans[b]
        tmp = aSpan.headFragment.from + aSpan.headFragment.to - bSpan.headFragment.from - bSpan.headFragment.to
        return (if tmp < 0 then -1 else 1)  if tmp
        0

      $.each sourceData.equivs, (equivNo, equiv) ->
        
        # equiv: ['*', 'Equiv', spanId...]
        equiv[0] = "*" + equivNo
        equivSpans = equiv.slice(2)
        okEquivSpans = []
        
        # collect the equiv spans in an array
        $.each equivSpans, (equivSpanNo, equivSpan) ->
          okEquivSpans.push equivSpan  if data.spans[equivSpan]

        
        # TODO: #404, inform the user with a message?
        
        # sort spans in the equiv by their midpoint
        okEquivSpans.sort spanComparator
        
        # generate the arcs
        len = okEquivSpans.length
        i = 1

        while i < len
          
          #           (id,                  triggerId,           roles,                         klass)
          eventDesc = data.eventDescs[equiv[0] + "*" + i] = new EventDesc(okEquivSpans[i - 1], okEquivSpans[i - 1], [[equiv[1], okEquivSpans[i]]], "equiv")
          eventDesc.leftSpans = okEquivSpans.slice(0, i)
          eventDesc.rightSpans = okEquivSpans.slice(i)
          i++

      $.each sourceData.relations, (relNo, rel) ->
        
        # rel[2] is args, rel[2][a][0] is role and rel[2][a][1] is value for a in (0,1)
        argsDesc = relationTypesHash[rel[1]]
        argsDesc = argsDesc and argsDesc.args
        t1 = undefined
        t2 = undefined
        if argsDesc
          
          # sort the arguments according to the config
          args = {}
          args[rel[2][0][0]] = rel[2][0][1]
          args[rel[2][1][0]] = rel[2][1][1]
          t1 = args[argsDesc[0].role]
          t2 = args[argsDesc[1].role]
        else
          
          # (or leave as-is in its absence)
          t1 = rel[2][0][1]
          t2 = rel[2][1][1]
        
        #           (id, triggerId, roles,          klass)
        data.eventDescs[rel[0]] = new EventDesc(t1, t1, [[rel[1], t2]], "relation")

      
      # attributes
      $.each sourceData.attributes, (attrNo, attr) ->
        
        # attr: [id, name, spanId, value, cueSpanId
        
        # TODO: might wish to check whats appropriate for the type
        # instead of using the first attribute def found
        attrType = (eventAttributeTypes[attr[1]] or entityAttributeTypes[attr[1]])
        attrValue = attrType and attrType.values[attrType.bool or attr[3]]
        span = data.spans[attr[2]]
        unless span
          dispatcher.post "messages", [[["Annotation " + attr[2] + ", referenced from attribute " + attr[0] + ", does not exist.", "error"]]]
          return
        valText = (attrValue and attrValue.name) or attr[3]
        attrText = (if attrType then ((if attrType.bool then attrType.name else (attrType.name + ": " + valText))) else ((if attr[3] is true then attr[1] else attr[1] + ": " + attr[3])))
        span.attributeText.push attrText
        span.attributes[attr[1]] = attr[3]
        if attr[4] # cue
          span.attributeCues[attr[1]] = attr[4]
          cueSpan = data.spans[attr[4]]
          cueSpan.attributeCueFor[data.spans[1]] = attr[2]
          cueSpan.cue = "CUE" # special css type
        $.extend span.attributeMerge, attrValue

      
      # comments
      $.each sourceData.comments, (commentNo, comment) ->
        
        # comment: [entityId, type, text]
        
        # TODO error handling
        
        # sentence id: ['sent', sentId]
        if comment[0] instanceof Array and comment[0][0] is "sent"
          
          # sentence comment
          sent = comment[0][1]
          text = comment[2]
          text = data.sentComment[sent].text + "<br/>" + text  if data.sentComment[sent]
          data.sentComment[sent] =
            type: comment[1]
            text: text
        else
          id = comment[0]
          trigger = triggerHash[id]
          eventDesc = data.eventDescs[id]
          # trigger: [span, ...]
          # span: [span]
          # arc: [eventDesc]
          commentEntities = (if trigger then trigger[1] else (if id of data.spans then [data.spans[id]] else (if id of data.eventDescs then [data.eventDescs[id]] else [])))
          $.each commentEntities, (entityId, entity) ->
            
            # if duplicate comment for entity:
            # overwrite type, concatenate comment with a newline
            unless entity.comment
              entity.comment =
                type: comment[1]
                text: comment[2]
            else
              entity.comment.type = comment[1]
              entity.comment.text += "\n" + comment[2]
            
            # partially duplicate marking of annotator note comments
            entity.annotatorNotes = comment[2]  if comment[1] is "AnnotatorNotes"
            
            # prioritize type setting when multiple comments are present
            entity.shadowClass = comment[1]  if commentPriority(comment[1]) > commentPriority(entity.shadowClass)


      
      # normalizations
      $.each sourceData.normalizations, (normNo, norm) ->
        id = norm[0]
        normType = norm[1]
        target = norm[2]
        refdb = norm[3]
        refid = norm[4]
        reftext = norm[5]
        
        # grab entity / event the normalization applies to
        span = data.spans[target]
        unless span
          dispatcher.post "messages", [[["Annotation " + target + ", referenced from normalization " + id + ", does not exist.", "error"]]]
          return
        
        # TODO: do we have any possible use for the normType?
        span.normalizations.push [refdb, refid, reftext]
        
        # quick hack for span box visual style
        span.normalized = "Normalized"

      
      # prepare span boundaries for token containment testing
      sortedFragments = []
      $.each data.spans, (spanNo, span) ->
        $.each span.fragments, (fragmentNo, fragment) ->
          sortedFragments.push fragment


      
      # sort fragments by beginning, then by end
      sortedFragments.sort (a, b) ->
        x = a.from
        y = b.from
        if x is y
          x = a.to
          y = b.to
        (if (x < y) then -1 else ((if (x > y) then 1 else 0)))

      currentFragmentId = 0
      startFragmentId = 0
      numFragments = sortedFragments.length
      lastTo = 0
      firstFrom = null
      chunkNo = 0
      space = undefined
      chunk = null
      
      # token containment testing (chunk recognition)
      $.each sourceData.token_offsets, ->
        from = this[0]
        to = this[1]
        firstFrom = from  if firstFrom is null
        
        # Replaced for speedup; TODO check correctness
        # inSpan = false;
        # $.each(data.spans, function(spanNo, span) {
        #   if (span.from < to && to < span.to) {
        #     // it does; no word break
        #     inSpan = true;
        #     return false;
        #   }
        # });
        
        # Is the token end inside a span?
        startFragmentId++  while startFragmentId < numFragments and to > sortedFragments[startFragmentId].from  if startFragmentId and to > sortedFragments[startFragmentId - 1].to
        currentFragmentId = startFragmentId
        currentFragmentId++  while currentFragmentId < numFragments and to >= sortedFragments[currentFragmentId].to
        
        # if yes, the next token is in the same chunk
        return  if currentFragmentId < numFragments and to > sortedFragments[currentFragmentId].from
        
        # otherwise, create the chunk found so far
        space = data.text.substring(lastTo, firstFrom)
        text = data.text.substring(firstFrom, to)
        chunk.nextSpace = space  if chunk
        
        #               (index,     text, from,      to, space) {
        chunk = new Chunk(chunkNo++, text, firstFrom, to, space)
        data.chunks.push chunk
        lastTo = to
        firstFrom = null

      numChunks = chunkNo
      
      # find sentence boundaries in relation to chunks
      chunkNo = 0
      sentenceNo = 0
      pastFirst = false
      $.each sourceData.sentence_offsets, ->
        from = this[0]
        return false  if chunkNo >= numChunks
        return  if data.chunks[chunkNo].from > from
        chunk = undefined
        chunkNo++  while chunkNo < numChunks and (chunk = data.chunks[chunkNo]).from < from
        chunkNo++
        if pastFirst and from <= chunk.from
          numNL = chunk.space.split("\n").length - 1
          numNL = 1  unless numNL
          sentenceNo += numNL
          chunk.sentence = sentenceNo
        else
          pastFirst = true

      
      # assign fragments to appropriate chunks
      currentChunkId = 0
      chunk = undefined
      $.each sortedFragments, (fragmentId, fragment) ->
        currentChunkId++  while fragment.to > (chunk = data.chunks[currentChunkId]).to
        chunk.fragments.push fragment
        fragment.text = chunk.text.substring(fragment.from - chunk.from, fragment.to - chunk.from)
        fragment.chunk = chunk

      
      # assign arcs to spans; calculate arc distances
      $.each data.eventDescs, (eventNo, eventDesc) ->
        dist = 0
        origin = data.spans[eventDesc.id]
        unless origin
          
          # TODO: include missing trigger ID in error message
          dispatcher.post "messages", [[["<strong>ERROR</strong><br/>Trigger for event \"" + eventDesc.id + "\" not found in " + data.document + "<br/>(please correct the source data)", "error", 5]]]
          return
        here = origin.headFragment.from + origin.headFragment.to
        $.each eventDesc.roles, (roleNo, role) ->
          target = data.spans[role.targetId]
          unless target
            dispatcher.post "messages", [[["<strong>ERROR</strong><br/>\"" + role.targetId + "\" (referenced from \"" + eventDesc.id + "\") not found in " + data.document + "<br/>(please correct the source data)", "error", 5]]]
            return
          there = target.headFragment.from + target.headFragment.to
          dist = Math.abs(here - there)
          arc = new Arc(eventDesc, role, dist, eventNo)
          origin.totalDist += dist
          origin.numArcs++
          target.totalDist += dist
          target.numArcs++
          data.arcs.push arc
          target.incoming.push arc
          origin.outgoing.push arc
          
          # ID dict for easy access. TODO: have a function defining the
          # (origin,type,target)->id mapping (see also annotator_ui.js)
          arcId = origin.id + "--" + role.type + "--" + target.id
          data.arcById[arcId] = arc


      # roles
      # eventDescs
      
      # highlighting
      markedText = []
      setMarked "edited" # set by editing process
      setMarked "focus" # set by URL
      setMarked "matchfocus" # set by search process, focused match
      setMarked "match" # set by search process, other (non-focused) match
      $.each data.spans, (spanId, span) ->
        
        # calculate average arc distances
        # average distance of arcs (0 for no arcs)
        span.avgDist = (if span.numArcs then span.totalDist / span.numArcs else 0)
        lastSpan = span
        
        # collect fragment texts into span texts
        fragmentTexts = []
        $.each span.fragments, (fragmentNo, fragment) ->
          
          # TODO heuristics
          fragmentTexts.push fragment.text

        span.text = fragmentTexts.join("")

      # data.spans
      i = 0

      while i < 2
        
        # preliminary sort to assign heights for basic cases
        # (first round) and cases resolved in the previous
        # round(s).
        $.each data.chunks, (chunkNo, chunk) ->
          
          # sort
          chunk.fragments.sort fragmentComparator
          
          # renumber
          $.each chunk.fragments, (fragmentNo, fragment) ->
            fragment.indexNumber = fragmentNo


        
        # nix the sums, so we can sum again
        $.each data.spans, (spanNo, span) ->
          span.refedIndexSum = 0

        
        # resolved cases will now have indexNumber set
        # to indicate their relative order. Sum those for referencing cases
        # for use in iterative resorting
        $.each data.arcs, (arcNo, arc) ->
          data.spans[arc.origin].refedIndexSum += data.spans[arc.target].headFragment.indexNumber

        i++
      
      # Final sort of fragments in chunks for drawing purposes
      # Also identify the marked text boundaries regarding chunks
      $.each data.chunks, (chunkNo, chunk) ->
        
        # and make the next sort take this into account. Note that this will
        # now resolve first-order dependencies between sort orders but not
        # second-order or higher.
        chunk.fragments.sort fragmentComparator
        $.each chunk.fragments, (fragmentNo, fragment) ->
          fragment.drawOrder = fragmentNo


      data.spanDrawOrderPermutation = Object.keys(data.spans)
      data.spanDrawOrderPermutation.sort (a, b) ->
        spanA = data.spans[a]
        spanB = data.spans[b]
        
        # We`re jumping all over the chunks, but it`s enough that
        # we`re doing everything inside each chunk in the right
        # order. should it become necessary to actually do these in
        # linear order, put in a similar condition for
        # spanX.headFragment.chunk.index; but it should not be
        # needed.
        tmp = spanA.headFragment.drawOrder - spanB.headFragment.drawOrder
        return (if tmp < 0 then -1 else 1)  if tmp
        0

      
      # resort the spans for linear order by center
      sortedFragments.sort midpointComparator
      
      # sort fragments into towers, calculate average arc distances
      lastFragment = null
      towerId = -1
      $.each sortedFragments, (i, fragment) ->
        towerId++  if not lastFragment or (lastFragment.from isnt fragment.from or lastFragment.to isnt fragment.to)
        fragment.towerId = towerId
        lastFragment = fragment

      # sortedFragments
      
      # find curlies (only the first fragment drawn in a tower)
      $.each data.spanDrawOrderPermutation, (spanIdNo, spanId) ->
        span = data.spans[spanId]
        $.each span.fragments, (fragmentNo, fragment) ->
          unless data.towers[fragment.towerId]
            data.towers[fragment.towerId] = []
            fragment.drawCurly = true
            fragment.span.drawCurly = true
          data.towers[fragment.towerId].push fragment


      spanAnnTexts = {}
      $.each data.chunks, (chunkNo, chunk) ->
        chunk.markedTextStart = []
        chunk.markedTextEnd = []
        $.each chunk.fragments, (fragmentNo, fragment) ->
          chunk.firstFragmentIndex = fragment.towerId  if chunk.firstFragmentIndex is `undefined`
          chunk.lastFragmentIndex = fragment.towerId
          spanLabels = Util.getSpanLabels(spanTypes, fragment.span.type)
          fragment.labelText = Util.spanDisplayForm(spanTypes, fragment.span.type)
          
          # Find the most appropriate label according to text width
          if Configuration.abbrevsOn and spanLabels
            labelIdx = 1 # first abbrev
            maxLength = (fragment.to - fragment.from) / 0.8
            while fragment.labelText.length > maxLength and spanLabels[labelIdx]
              fragment.labelText = spanLabels[labelIdx]
              labelIdx++
          svgtext = svg.createText() # one "text" element per row
          postfixArray = []
          prefix = ""
          postfix = ""
          warning = false
          $.each fragment.span.attributes, (attrType, valType) ->
            
            # TODO: might wish to check what*s appropriate for the type
            # instead of using the first attribute def found
            attr = (eventAttributeTypes[attrType] or entityAttributeTypes[attrType])
            unless attr
              
              # non-existent type
              warning = true
              return
            val = attr.values[attr.bool or valType]
            unless val
              
              # non-existent value
              warning = true
              return
            if $.isEmptyObject(val)
              
              # defined, but lacks any visual presentation
              warning = true
              return
            if val.glyph
              if val.position is "left"
                prefix = val.glyph + prefix
                css = "glyph"
                css += " glyph_" + Util.escapeQuotes(attr.css)  if attr.css
                svgtext.span val.glyph,
                  class: css

              else # XXX right is implied - maybe change
                postfixArray.push [attr, val]
                postfix += val.glyph

          text = fragment.labelText
          if prefix isnt ""
            text = prefix + " " + text
            svgtext.string " "
          svgtext.string fragment.labelText
          if postfixArray.length
            text += " " + postfix
            svgtext.string " "
            $.each postfixArray, (elNo, el) ->
              css = "glyph"
              css += " glyph_" + Util.escapeQuotes(el[0].css)  if el[0].css
              svgtext.span el[1].glyph,
                class: css


          if warning
            svgtext.span "#",
              class: "glyph attribute_warning"

            text += " #"
          fragment.glyphedLabelText = text
          unless spanAnnTexts[text]
            spanAnnTexts[text] = true
            data.spanAnnTexts[text] = svgtext


      # chunk.fragments
      # chunks
      numChunks = data.chunks.length
      
      # note the location of marked text with respect to chunks
      startChunk = 0
      currentChunk = undefined
      
      # sort by "from"; we don't need to sort by "to" as well,
      # because unlike spans, chunks are disjunct
      markedText.sort (a, b) ->
        Util.cmp a[0], b[0]

      $.each markedText, (textNo, textPos) ->
        from = textPos[0]
        to = textPos[1]
        markedType = textPos[2]
        from = 0  if from < 0
        to = 0  if to < 0
        to = data.text.length - 1  if to >= data.text.length
        from = to  if from > to
        while startChunk < numChunks
          chunk = data.chunks[startChunk]
          if from <= chunk.to
            chunk.markedTextStart.push [textNo, true, from - chunk.from, null, markedType]
            break
          startChunk++
        if startChunk is numChunks
          dispatcher.post "messages", [[["Wrong text offset", "error"]]]
          return
        currentChunk = startChunk
        while currentChunk < numChunks
          chunk = data.chunks[currentChunk]
          if to <= chunk.to
            chunk.markedTextEnd.push [textNo, false, to - chunk.from]
            break
          currentChunk++
        if currentChunk is numChunks
          dispatcher.post "messages", [[["Wrong text offset", "error"]]]
          chunk = data.chunks[data.chunks.length - 1]
          chunk.markedTextEnd.push [textNo, false, chunk.text.length]
          return

      # markedText
      dispatcher.post "dataReady", [data]

    resetData = ->
      setData sourceData
      renderData()

    translate = (element, x, y) ->
      $(element.group).attr "transform", "translate(" + x + ", " + y + ")"
      element.translation =
        x: x
        y: y

    showMtime = ->
      if data.mtime
        
        # we're getting seconds and need milliseconds
        #$('#document_ctime').text("Created: " + Annotator.formatTime(1000 * data.ctime)).css("display", "inline");
        $("#document_mtime").text("Last modified: " + Util.formatTimeAgo(1000 * data.mtime)).css "display", "inline"
      else
        
        #$('#document_ctime').css("display", "none");
        $("#document_mtime").css "display", "none"

    addHeaderAndDefs = ->
      commentName = (coll + "/" + doc).replace("--", "-\\-")
      $svg.append "<!-- document: " + commentName + " -->"
      defs = svg.defs()
      $blurFilter = $("<filter id=\"Gaussian_Blur\"><feGaussianBlur in=\"SourceGraphic\" stdDeviation=\"2\" /></filter>")
      svg.add defs, $blurFilter
      defs

    getTextMeasurements = (textsHash, options, callback) ->
      
      # make some text elements, find out the dimensions
      textMeasureGroup = svg.group(options)
      
      # changed from $.each because of #264 ('length' can appear)
      for text of textsHash
        svg.text textMeasureGroup, 0, 0, text  if textsHash.hasOwnProperty(text)
      
      # measuring goes on here
      widths = {}
      $(textMeasureGroup).find("text").each (svgTextNo, svgText) ->
        text = $(svgText).text()
        widths[text] = @getComputedTextLength()
        if callback
          $.each textsHash[text], (text, object) ->
            callback object, svgText


      bbox = textMeasureGroup.getBBox()
      svg.remove textMeasureGroup
      new Measurements(widths, bbox.height, bbox.y)

    getTextAndSpanTextMeasurements = ->
      
      # get the span text sizes
      chunkTexts = {} # set of span texts
      $.each data.chunks, (chunkNo, chunk) ->
        chunk.row = `undefined` # reset
        chunkTexts[chunk.text] = []  unless chunk.text of chunkTexts
        chunkText = chunkTexts[chunk.text]
        
        # here we also need all the spans that are contained in
        # chunks with this text, because we need to know the position
        # of the span text within the respective chunk text
        chunkText.push.apply chunkText, chunk.fragments
        
        # and also the markedText boundaries
        chunkText.push.apply chunkText, chunk.markedTextStart
        chunkText.push.apply chunkText, chunk.markedTextEnd

      textSizes = getTextMeasurements(chunkTexts, `undefined`, (fragment, text) ->
        if fragment instanceof Fragment # it*s a fragment!
          # measure the fragment text position in pixels
          firstChar = fragment.from - fragment.chunk.from
          if firstChar < 0
            firstChar = 0
            dispatcher.post "messages", [[["<strong>WARNING</strong>" + "<br/> " + "The fragment [" + fragment.from + ", " + fragment.to + "] (" + fragment.text + ") is not " + "contained in its designated chunk [" + fragment.chunk.from + ", " + fragment.chunk.to + "] most likely " + "due to the fragment starting or ending with a space, please " + "verify the sanity of your data since we are unable to " + "visualise this fragment correctly and will drop leading " + "space characters", "warning", 15]]]
          lastChar = fragment.to - fragment.chunk.from - 1
          
          # Adjust for XML whitespace (#832, #1009)
          textUpToFirstChar = fragment.chunk.text.substring(0, firstChar)
          textUpToLastChar = fragment.chunk.text.substring(0, lastChar)
          textUpToFirstCharUnspaced = textUpToFirstChar.replace(/\s\s+/g, " ")
          textUpToLastCharUnspaced = textUpToLastChar.replace(/\s\s+/g, " ")
          firstChar -= textUpToFirstChar.length - textUpToFirstCharUnspaced.length
          lastChar -= textUpToLastChar.length - textUpToLastCharUnspaced.length
          startPos = text.getStartPositionOfChar(firstChar).x
          endPos = (if (lastChar < 0) then startPos else text.getEndPositionOfChar(lastChar).x)
          fragment.curly =
            from: startPos
            to: endPos
        else # it*s markedText [id, start?, char#, offset]
          fragment[2] = 0  if fragment[2] < 0
          unless fragment[2] # start
            fragment[3] = text.getStartPositionOfChar(fragment[2]).x
          else
            fragment[3] = text.getEndPositionOfChar(fragment[2] - 1).x + 1
      )
      
      # get the fragment annotation text sizes
      fragmentTexts = {}
      noSpans = true
      $.each data.spans, (spanNo, span) ->
        $.each span.fragments, (fragmentNo, fragment) ->
          fragmentTexts[fragment.glyphedLabelText] = true
          noSpans = false


      fragmentTexts.$ = true  if noSpans # dummy so we can at least get the height
      fragmentSizes = getTextMeasurements(fragmentTexts,
        class: "span"
      )
      texts: textSizes
      fragments: fragmentSizes

    addArcTextMeasurements = (sizes) ->
      
      # get the arc annotation text sizes (for all labels)
      arcTexts = {}
      $.each data.arcs, (arcNo, arc) ->
        labels = Util.getArcLabels(spanTypes, data.spans[arc.origin].type, arc.type, relationTypesHash)
        labels = [arc.type]  unless labels.length
        $.each labels, (labelNo, label) ->
          arcTexts[label] = true


      arcSizes = getTextMeasurements(arcTexts,
        class: "arcs"
      )
      sizes.arcs = arcSizes

    adjustTowerAnnotationSizes = ->
      
      # find biggest annotation in each tower
      $.each data.towers, (towerNo, tower) ->
        maxWidth = 0
        $.each tower, (fragmentNo, fragment) ->
          width = data.sizes.fragments.widths[fragment.glyphedLabelText]
          maxWidth = width  if width > maxWidth

        # tower
        $.each tower, (fragmentNo, fragment) ->
          fragment.width = maxWidth



    # tower
    # data.towers
    makeArrow = (defs, spec) ->
      parsedSpec = spec.split(",")
      type = parsedSpec[0]
      return  if type is "none"
      width = 5
      height = 5
      color = "black"
      if $.isNumeric(parsedSpec[1]) and parsedSpec[2]
        if $.isNumeric(parsedSpec[2]) and parsedSpec[3]
          
          # 3 args, 2 numeric: assume width, height, color
          width = parsedSpec[1]
          height = parsedSpec[2]
          color = parsedSpec[3] or "black"
        else
          
          # 2 args, 1 numeric: assume width/height, color
          width = height = parsedSpec[1]
          color = parsedSpec[2] or "black"
      else
        
        # other: assume color only
        width = height = 5
        color = parsedSpec[1] or "black"
      
      # hash needs to be replaced as IDs don't permit it.
      arrowId = "arrow_" + spec.replace(/#/g, "").replace(/,/g, "_")
      arrow = undefined
      if type is "triangle"
        arrow = svg.marker(defs, arrowId, width, height / 2, width, height, "auto",
          markerUnits: "strokeWidth"
          fill: color
        )
        svg.polyline arrow, [[0, 0], [width, height / 2], [0, height], [width / 12, height / 2]]
      arrowId

    drawing = false
    redraw = false
    renderDataReal = (sourceData) ->
      Util.profileEnd "before render"
      Util.profileStart "render"
      Util.profileStart "init"
      if not sourceData and not data
        dispatcher.post "doneRendering", [coll, doc, args]
        return
      $svgDiv.show()
      if (sourceData and sourceData.collection and (sourceData.document isnt doc or sourceData.collection isnt coll)) or drawing
        redraw = true
        dispatcher.post "doneRendering", [coll, doc, args]
        return
      redraw = false
      drawing = true
      setData sourceData  if sourceData
      showMtime()
      
      # clear the SVG
      svg.clear true
      return  if not data or data.length is 0
      
      # establish the width according to the enclosing element
      canvasWidth = that.forceWidth or $svgDiv.width()
      defs = addHeaderAndDefs()
      backgroundGroup = svg.group(class: "background")
      glowGroup = svg.group(class: "glow")
      highlightGroup = svg.group(class: "highlight")
      textGroup = svg.group(class: "text")
      Util.profileEnd "init"
      Util.profileStart "measures"
      sizes = getTextAndSpanTextMeasurements()
      data.sizes = sizes
      adjustTowerAnnotationSizes()
      maxTextWidth = 0
      for text of sizes.texts.widths
        if sizes.texts.widths.hasOwnProperty(text)
          width = sizes.texts.widths[text]
          maxTextWidth = width  if width > maxTextWidth
      Util.profileEnd "measures"
      Util.profileStart "chunks"
      currentX = Configuration.visual.margin.x + sentNumMargin + rowPadding
      rows = []
      fragmentHeights = []
      sentenceToggle = 0
      sentenceNumber = 0
      row = new Row(svg)
      row.sentence = ++sentenceNumber
      row.backgroundIndex = sentenceToggle
      row.index = 0
      rowIndex = 0
      twoBarWidths = undefined # HACK to avoid measuring space*s width
      openTextHighlights = {}
      textMarkedRows = []
      addArcTextMeasurements sizes
      
      # reserve places for spans
      floors = []
      reservations = [] # reservations[chunk][floor] = [[from, to, headroom]...]
      i = 0

      while i <= data.lastFragmentIndex
        reservation[i] = {}
        i++
      inf = 1.0 / 0.0
      $.each data.spanDrawOrderPermutation, (spanIdNo, spanId) ->
        span = data.spans[spanId]
        f1 = span.fragments[0]
        f2 = span.fragments[span.fragments.length - 1]
        x1 = (f1.curly.from + f1.curly.to - f1.width) / 2 - Configuration.visual.margin.x
        i1 = f1.chunk.index
        x2 = (f2.curly.from + f2.curly.to + f2.width) / 2 + Configuration.visual.margin.x
        i2 = f2.chunk.index
        
        # Start from the ground level, going up floor by floor.
        # If no more floors, make a new available one.
        # If a floor is available and there is no carpet, mark it as carpet.
        # If a floor is available and there is carpet and height
        #   difference is at least fragment height + curly, OK.
        # If a floor is not available, forget about carpet.
        # --
        # When OK, calculate exact ceiling.
        # If there isn't one, make a new floor, copy reservations
        #   from floor below (with decreased ceiling)
        # Make the reservation from the carpet to just below the
        #   current floor.
        #
        # TODO drawCurly and height could be prettified to only check
        # actual positions of curlies
        carpet = 0
        outside = true
        thisCurlyHeight = (if span.drawCurly then Configuration.visual.curlyHeight else 0)
        height = sizes.fragments.height + thisCurlyHeight + Configuration.visual.boxSpacing + 2 * Configuration.visual.margin.y - 3
        $.each floors, (floorNo, floor) ->
          floorAvailable = true
          i = i1

          while i <= i2
            continue  unless reservations[i] and reservations[i][floor]
            from = (if (i is i1) then x1 else -inf)
            to = (if (i is i2) then x2 else inf)
            $.each reservations[i][floor], (resNo, res) ->
              if res[0] < to and from < res[1]
                floorAvailable = false
                false

            i++
          if floorAvailable
            if carpet is null
              carpet = floor
            else if height + carpet <= floor
              
              # found our floor!
              outside = false
              false
          else
            carpet = null

        reslen = reservations.length
        makeNewFloorIfNeeded = (floor) ->
          floorNo = $.inArray(floor, floors)
          if floorNo is -1
            floors.push floor
            floors.sort Util.cmp
            floorNo = $.inArray(floor, floors)
            unless floorNo is 0
              
              # copy reservations from the floor below
              parquet = floors[floorNo - 1]
              i = 0

              while i <= reslen
                if reservations[i]
                  reservations[i][parquet] = []  unless reservations[i][parquet]
                  footroom = floor - parquet
                  $.each reservations[i][parquet], (resNo, res) ->
                    if res[2] > footroom
                      reservations[i][floor] = []  unless reservations[i][floor]
                      reservations[i][floor].push [res[0], res[1], res[2] - footroom]

                i++
          floorNo

        ceiling = carpet + height
        ceilingNo = makeNewFloorIfNeeded(ceiling)
        carpetNo = makeNewFloorIfNeeded(carpet)
        
        # make the reservation
        floor = undefined
        floorNo = undefined
        floorNo = carpetNo
        while (floor = floors[floorNo]) isnt `undefined` and floor < ceiling
          headroom = ceiling - floor
          i = i1

          while i <= i2
            from = (if (i is i1) then x1 else 0)
            to = (if (i is i2) then x2 else inf)
            reservations[i] = {}  unless reservations[i]
            reservations[i][floor] = []  unless reservations[i][floor]
            reservations[i][floor].push [from, to, headroom] # XXX maybe add fragment; probably unnecessary
            i++
          floorNo++
        span.floor = carpet + thisCurlyHeight

      $.each data.chunks, (chunkNo, chunk) ->
        reservations = new Array()
        chunk.group = svg.group(row.group)
        chunk.highlightGroup = svg.group(chunk.group)
        y = 0
        minArcDist = undefined
        hasLeftArcs = undefined
        hasRightArcs = undefined
        hasInternalArcs = undefined
        hasAnnotations = undefined
        chunkFrom = Infinity
        chunkTo = 0
        chunkHeight = 0
        spacing = 0
        spacingChunkId = null
        spacingRowBreak = 0
        $.each chunk.fragments, (fragmentNo, fragment) ->
          span = fragment.span
          spanDesc = spanTypes[span.type]
          bgColor = ((spanDesc and spanDesc.bgColor) or (spanTypes.SPAN_DEFAULT and spanTypes.SPAN_DEFAULT.bgColor) or "#ffffff")
          fgColor = ((spanDesc and spanDesc.fgColor) or (spanTypes.SPAN_DEFAULT and spanTypes.SPAN_DEFAULT.fgColor) or "#000000")
          borderColor = ((spanDesc and spanDesc.borderColor) or (spanTypes.SPAN_DEFAULT and spanTypes.SPAN_DEFAULT.borderColor) or "#000000")
          
          # special case: if the border 'color' value is 'darken',
          # then just darken the BG color a bit for the border.
          borderColor = Util.adjustColorLightness(bgColor, -0.6)  if borderColor is "darken"
          fragment.group = svg.group(chunk.group,
            class: "span"
          )
          fragmentHeight = 0
          y = -sizes.texts.height  unless y
          x = (fragment.curly.from + fragment.curly.to) / 2
          
          # XXX is it maybe sizes.texts?
          yy = y + sizes.fragments.y
          hh = sizes.fragments.height
          ww = fragment.width
          xx = x - ww / 2
          
          # text margin fine-tuning
          yy += boxTextMargin.y
          hh -= 2 * boxTextMargin.y
          xx += boxTextMargin.x
          ww -= 2 * boxTextMargin.x
          rectClass = "span_" + (span.cue or span.type) + " span_default" # TODO XXX first part unneeded I think; remove
          
          # attach e.g. "False_positive" into the type
          rectClass += " " + span.comment.type  if span.comment and span.comment.type
          bx = xx - Configuration.visual.margin.x - boxTextMargin.x
          by_ = yy - Configuration.visual.margin.y
          bw = ww + 2 * Configuration.visual.margin.x
          bh = hh + 2 * Configuration.visual.margin.y
          if roundCoordinates
            x = (x | 0) + 0.5
            bx = (bx | 0) + 0.5
          shadowRect = undefined
          markedRect = undefined
          if span.marked
            markedRect = svg.rect(chunk.highlightGroup, bx - markedSpanSize, by_ - markedSpanSize, bw + 2 * markedSpanSize, bh + 2 * markedSpanSize,
              
              # filter: 'url(#Gaussian_Blur)',
              class: "shadow_EditHighlight"
              rx: markedSpanSize
              ry: markedSpanSize
            )
            svg.other markedRect, "animate",
              "data-type": span.marked
              attributeName: "fill"
              values: ((if span.marked is "match" then highlightMatchSequence else highlightSpanSequence))
              dur: highlightDuration
              repeatCount: "indefinite"
              begin: "indefinite"

            chunkFrom = Math.min(bx - markedSpanSize, chunkFrom)
            chunkTo = Math.max(bx + bw + markedSpanSize, chunkTo)
            fragmentHeight = Math.max(bh + 2 * markedSpanSize, fragmentHeight)
          
          # .match() removes unconfigured shadows, which were
          # always showing up as black.
          # TODO: don't hard-code configured shadowclasses.
          if span.shadowClass and span.shadowClass.match("True_positive|False_positive|False_negative|AnnotationError|AnnotationWarning|AnnotatorNotes|Normalized|AnnotationIncomplete|AnnotationUnconfirmed|rectEditHighlight|EditHighlight_arc|MissingAnnotation|ChangedAnnotation ")
            shadowRect = svg.rect(fragment.group, bx - rectShadowSize, by_ - rectShadowSize, bw + 2 * rectShadowSize, bh + 2 * rectShadowSize,
              class: "shadow_" + span.shadowClass
              filter: "url(#Gaussian_Blur)"
              rx: rectShadowRounding
              ry: rectShadowRounding
            )
            chunkFrom = Math.min(bx - rectShadowSize, chunkFrom)
            chunkTo = Math.max(bx + bw + rectShadowSize, chunkTo)
            fragmentHeight = Math.max(bh + 2 * rectShadowSize, fragmentHeight)
          fragment.rect = svg.rect(fragment.group, bx, by_, bw, bh,
            class: rectClass
            fill: bgColor
            stroke: borderColor
            rx: Configuration.visual.margin.x
            ry: Configuration.visual.margin.y
            "data-span-id": span.id
            "data-fragment-id": span.segmentedOffsetsMap[fragment.id]
            strokeDashArray: span.attributeMerge.dashArray
          )
          
          # TODO XXX: quick nasty hack to allow normalizations
          # to be marked visually; do something cleaner!
          $(fragment.rect).addClass span.normalized  if span.normalized
          fragment.right = bx + bw # TODO put it somewhere nicer?
          unless span.shadowClass or span.marked
            chunkFrom = Math.min(bx, chunkFrom)
            chunkTo = Math.max(bx + bw, chunkTo)
            fragmentHeight = Math.max(bh, fragmentHeight)
          fragment.rectBox =
            x: bx
            y: by_ - span.floor
            width: bw
            height: bh

          fragment.height = span.floor + hh + 3 * Configuration.visual.margin.y + Configuration.visual.curlyHeight + Configuration.visual.arcSpacing
          spacedTowerId = fragment.towerId * 2
          fragmentHeights[spacedTowerId] = fragment.height  if not fragmentHeights[spacedTowerId] or fragmentHeights[spacedTowerId] < fragment.height
          $(fragment.rect).attr "y", yy - Configuration.visual.margin.y - span.floor
          $(shadowRect).attr "y", yy - rectShadowSize - Configuration.visual.margin.y - span.floor  if shadowRect
          $(markedRect).attr "y", yy - markedSpanSize - Configuration.visual.margin.y - span.floor  if markedRect
          if span.attributeMerge.box is "crossed"
            svg.path fragment.group, svg.createPath().move(xx, yy - Configuration.visual.margin.y - span.floor).line(xx + fragment.width, yy + hh + Configuration.visual.margin.y - span.floor),
              class: "boxcross"

            svg.path fragment.group, svg.createPath().move(xx + fragment.width, yy - Configuration.visual.margin.y - span.floor).line(xx, yy + hh + Configuration.visual.margin.y - span.floor),
              class: "boxcross"

          svg.text fragment.group, x, y - span.floor, data.spanAnnTexts[fragment.glyphedLabelText],
            fill: fgColor

          
          # Make curlies to show the fragment
          if fragment.drawCurly
            curlyColor = "grey"
            if coloredCurlies
              spanDesc = spanTypes[span.type]
              bgColor = ((spanDesc and spanDesc.bgColor) or (spanTypes.SPAN_DEFAULT and spanTypes.SPAN_DEFAULT.fgColor) or "#000000")
              curlyColor = Util.adjustColorLightness(bgColor, -0.6)
            bottom = yy + hh + Configuration.visual.margin.y - span.floor + 1
            svg.path fragment.group, svg.createPath().move(fragment.curly.from, bottom + Configuration.visual.curlyHeight).curveC(fragment.curly.from, bottom, x, bottom + Configuration.visual.curlyHeight, x, bottom).curveC(x, bottom + Configuration.visual.curlyHeight, fragment.curly.to, bottom, fragment.curly.to, bottom + Configuration.visual.curlyHeight),
              class: "curly"
              stroke: curlyColor

            chunkFrom = Math.min(fragment.curly.from, chunkFrom)
            chunkTo = Math.max(fragment.curly.to, chunkTo)
            fragmentHeight = Math.max(Configuration.visual.curlyHeight, fragmentHeight)
          if fragment is span.headFragment
            
            # find the gap to fit the backwards arcs, but only on
            # head fragment - other fragments don't have arcs
            $.each span.incoming, (arcId, arc) ->
              leftSpan = data.spans[arc.origin]
              origin = leftSpan.headFragment.chunk
              border = undefined
              hasInternalArcs = true  if chunk.index is origin.index
              if origin.row
                labels = Util.getArcLabels(spanTypes, leftSpan.type, arc.type, relationTypesHash)
                labels = [arc.type]  unless labels.length
                if origin.row.index is rowIndex
                  
                  # same row, but before this
                  border = origin.translation.x + leftSpan.fragments[leftSpan.fragments.length - 1].right
                else
                  border = Configuration.visual.margin.x + sentNumMargin + rowPadding
                labelNo = (if Configuration.abbrevsOn then labels.length - 1 else 0)
                smallestLabelWidth = sizes.arcs.widths[labels[labelNo]] + 2 * minArcSlant
                gap = currentX + bx - border
                arcSpacing = smallestLabelWidth - gap
                if not hasLeftArcs or spacing < arcSpacing
                  spacing = arcSpacing
                  spacingChunkId = origin.index + 1
                arcSpacing = smallestLabelWidth - bx
                spacingRowBreak = arcSpacing  if not hasLeftArcs or spacingRowBreak < arcSpacing
                hasLeftArcs = true
              else
                hasRightArcs = true

            $.each span.outgoing, (arcId, arc) ->
              leftSpan = data.spans[arc.target]
              target = leftSpan.headFragment.chunk
              border = undefined
              if target.row
                labels = Util.getArcLabels(spanTypes, span.type, arc.type, relationTypesHash)
                labels = [arc.type]  unless labels.length
                if target.row.index is rowIndex
                  
                  # same row, but before this
                  border = target.translation.x + leftSpan.fragments[leftSpan.fragments.length - 1].right
                else
                  border = Configuration.visual.margin.x + sentNumMargin + rowPadding
                labelNo = (if Configuration.abbrevsOn then labels.length - 1 else 0)
                smallestLabelWidth = sizes.arcs.widths[labels[labelNo]] + 2 * minArcSlant
                gap = currentX + bx - border
                arcSpacing = smallestLabelWidth - gap
                if not hasLeftArcs or spacing < arcSpacing
                  spacing = arcSpacing
                  spacingChunkId = target.index + 1
                arcSpacing = smallestLabelWidth - bx
                spacingRowBreak = arcSpacing  if not hasLeftArcs or spacingRowBreak < arcSpacing
                hasLeftArcs = true
              else
                hasRightArcs = true

          fragmentHeight += span.floor or Configuration.visual.curlyHeight
          chunkHeight = fragmentHeight  if fragmentHeight > chunkHeight
          hasAnnotations = true

        # fragments
        
        # positioning of the chunk
        chunk.right = chunkTo
        textWidth = sizes.texts.widths[chunk.text]
        chunkHeight += sizes.texts.height
        boxX = -Math.min(chunkFrom, 0)
        boxWidth = Math.max(textWidth, chunkTo) - Math.min(0, chunkFrom)
        
        # if (hasLeftArcs) {
        # TODO change this with smallestLeftArc
        # var spacing = arcHorizontalSpacing - (currentX - lastArcBorder);
        # arc too small?
        currentX += spacing  if spacing > 0
        
        # }
        rightBorderForArcs = (if hasRightArcs then arcHorizontalSpacing else ((if hasInternalArcs then arcSlant else 0)))
        lastX = currentX
        lastRow = row
        if chunk.sentence
          while sentenceNumber < chunk.sentence
            sentenceNumber++
            row.arcs = svg.group(row.group,
              class: "arcs"
            )
            rows.push row
            row = new Row(svg)
            sentenceToggle = 1 - sentenceToggle
            row.backgroundIndex = sentenceToggle
            row.index = ++rowIndex
          sentenceToggle = 1 - sentenceToggle
        if chunk.sentence or currentX + boxWidth + rightBorderForArcs >= canvasWidth - 2 * Configuration.visual.margin.x
          
          # the chunk does not fit
          row.arcs = svg.group(row.group,
            class: "arcs"
          )
          
          # TODO: related to issue #571
          # replace arcHorizontalSpacing with a calculated value
          currentX = Configuration.visual.margin.x + sentNumMargin + rowPadding + ((if hasLeftArcs then arcHorizontalSpacing else ((if hasInternalArcs then arcSlant else 0))))
          if hasLeftArcs
            adjustedCurTextWidth = sizes.texts.widths[chunk.text] + arcHorizontalSpacing
            maxTextWidth = adjustedCurTextWidth  if adjustedCurTextWidth > maxTextWidth
          if spacingRowBreak > 0
            currentX += spacingRowBreak
            spacing = 0 # do not center intervening elements
          
          # new row
          rows.push row
          svg.remove chunk.group
          row = new Row(svg)
          row.backgroundIndex = sentenceToggle
          row.index = ++rowIndex
          svg.add row.group, chunk.group
          chunk.group = row.group.lastElementChild
          $(chunk.group).children("g[class='span']").each (index, element) ->
            chunk.fragments[index].group = element

          $(chunk.group).find("rect[data-span-id]").each (index, element) ->
            chunk.fragments[index].rect = element

        
        # break the text highlights when the row breaks
        if row.index isnt lastRow.index
          $.each openTextHighlights, (textId, textDesc) ->
            unless textDesc[3] is lastX
              newDesc = [lastRow, textDesc[3], lastX + boxX, textDesc[4]]
              textMarkedRows.push newDesc
            textDesc[3] = currentX

        
        # open text highlights
        $.each chunk.markedTextStart, (textNo, textDesc) ->
          textDesc[3] += currentX + boxX
          openTextHighlights[textDesc[0]] = textDesc

        
        # close text highlights
        $.each chunk.markedTextEnd, (textNo, textDesc) ->
          textDesc[3] += currentX + boxX
          startDesc = openTextHighlights[textDesc[0]]
          delete openTextHighlights[textDesc[0]]

          markedRow = [row, startDesc[3], textDesc[3], startDesc[4]]
          textMarkedRows.push markedRow

        
        # XXX check this - is it used? should it be lastRow?
        row.hasAnnotations = true  if hasAnnotations
        row.sentence = ++sentenceNumber  if chunk.sentence
        if spacing > 0
          
          # if we added a gap, center the intervening elements
          spacing /= 2
          firstChunkInRow = row.chunks[row.chunks.length - 1]
          spacingChunkId = firstChunkInRow.index + 1  if spacingChunkId < firstChunkInRow.index
          chunkIndex = spacingChunkId

          while chunkIndex < chunk.index
            movedChunk = data.chunks[chunkIndex]
            translate movedChunk, movedChunk.translation.x + spacing, 0
            movedChunk.textX += spacing
            chunkIndex++
        row.chunks.push chunk
        chunk.row = row
        translate chunk, currentX + boxX, 0
        chunk.textX = currentX + boxX
        spaceWidth = 0
        spaceLen = chunk.nextSpace and chunk.nextSpace.length or 0
        i = 0

        while i < spaceLen
          spaceWidth += spaceWidths[chunk.nextSpace[i]] or 0
          i++
        currentX += spaceWidth + boxWidth

      # chunks
      
      # finish the last row
      row.arcs = svg.group(row.group,
        class: "arcs"
      )
      rows.push row
      Util.profileEnd "chunks"
      Util.profileStart "arcsPrep"
      arrows = {}
      arrow = makeArrow(defs, "none")
      arrows["none"] = arrow  if arrow
      len = fragmentHeights.length
      i = 0

      while i < len
        fragmentHeights[i] = Configuration.visual.arcStartHeight  if not fragmentHeights[i] or fragmentHeights[i] < Configuration.visual.arcStartHeight
        i++
      
      # find out how high the arcs have to go
      $.each data.arcs, (arcNo, arc) ->
        arc.jumpHeight = 0
        fromFragment = data.spans[arc.origin].headFragment
        toFragment = data.spans[arc.target].headFragment
        if fromFragment.towerId > toFragment.towerId
          tmp = fromFragment
          fromFragment = toFragment
          toFragment = tmp
        from = undefined
        to = undefined
        if fromFragment.chunk.index is toFragment.chunk.index
          from = fromFragment.towerId
          to = toFragment.towerId
        else
          from = fromFragment.towerId + 1
          to = toFragment.towerId - 1
        i = from

        while i <= to
          arc.jumpHeight = fragmentHeights[i * 2]  if arc.jumpHeight < fragmentHeights[i * 2]
          i++

      
      # sort the arcs
      data.arcs.sort (a, b) ->
        
        # first write those that have less to jump over
        tmp = a.jumpHeight - b.jumpHeight
        return (if tmp < 0 then -1 else 1)  if tmp
        
        # if equal, then those that span less distance
        tmp = a.dist - b.dist
        return (if tmp < 0 then -1 else 1)  if tmp
        
        # if equal, then those where heights of the targets are smaller
        tmp = data.spans[a.origin].headFragment.height + data.spans[a.target].headFragment.height - data.spans[b.origin].headFragment.height - data.spans[b.target].headFragment.height
        return (if tmp < 0 then -1 else 1)  if tmp
        
        # if equal, then those with the lower origin
        tmp = data.spans[a.origin].headFragment.height - data.spans[b.origin].headFragment.height
        return (if tmp < 0 then -1 else 1)  if tmp
        
        # if equal, they're just equal.
        0

      
      # see which fragments are in each row
      heightsStart = 0
      heightsRowsAdded = 0
      $.each rows, (rowId, row) ->
        seenFragment = false
        row.heightsStart = row.heightsEnd = heightsStart
        $.each row.chunks, (chunkId, chunk) ->
          if chunk.lastFragmentIndex isnt `undefined`
            
            # fragmentful chunk
            seenFragment = true
            heightsIndex = chunk.lastFragmentIndex * 2 + heightsRowsAdded
            row.heightsEnd = heightsIndex  if row.heightsEnd < heightsIndex
            heightsIndex = chunk.firstFragmentIndex * 2 + heightsRowsAdded
            row.heightsStart = heightsIndex  if row.heightsStart > heightsIndex

        fragmentHeights.splice row.heightsStart, 0, Configuration.visual.arcStartHeight
        heightsRowsAdded++
        row.heightsAdjust = heightsRowsAdded
        row.heightsEnd += 2  if seenFragment
        heightsStart = row.heightsEnd + 1

      
      # draw the drag arc marker
      arrowhead = svg.marker(defs, "drag_arrow", 5, 2.5, 5, 5, "auto",
        markerUnits: "strokeWidth"
        class: "drag_fill"
      )
      svg.polyline arrowhead, [[0, 0], [5, 2.5], [0, 5], [0.2, 2.5]]
      arcDragArc = svg.path(svg.createPath(),
        markerEnd: "url(#drag_arrow)"
        class: "drag_stroke"
        fill: "none"
        visibility: "hidden"
      )
      dispatcher.post "arcDragArcDrawn", [arcDragArc]
      Util.profileEnd "arcsPrep"
      Util.profileStart "arcs"
      arcCache = {}
      
      # add the arcs
      $.each data.arcs, (arcNo, arc) ->
        
        # separate out possible numeric suffix from type
        noNumArcType = undefined
        splitArcType = undefined
        if arc.type
          splitArcType = arc.type.match(/^(.*?)(\d*)$/)
          noNumArcType = splitArcType[1]
        originSpan = data.spans[arc.origin]
        targetSpan = data.spans[arc.target]
        leftToRight = originSpan.headFragment.towerId < targetSpan.headFragment.towerId
        left = undefined
        right = undefined
        if leftToRight
          left = originSpan.headFragment
          right = targetSpan.headFragment
        else
          left = targetSpan.headFragment
          right = originSpan.headFragment
        
        # fall back on relation types in case we still don't have
        # an arc description, with final fallback to unnumbered relation
        arcDesc = relationTypesHash[arc.type] or relationTypesHash[noNumArcType]
        
        # if it*s not a relationship, see if we can find it in span
        # descriptions
        # TODO: might make more sense to reformat this as dict instead
        # of searching through the list every type
        spanDesc = spanTypes[originSpan.type]
        if not arcDesc and spanDesc and spanDesc.arcs
          $.each spanDesc.arcs, (arcDescNo, arcDescIter) ->
            arcDesc = arcDescIter  if arcDescIter.type is arc.type

        
        # last fall back on unnumbered type if not found in full
        if not arcDesc and noNumArcType and noNumArcType isnt arc.type and spanDesc and spanDesc.arcs
          $.each spanDesc.arcs, (arcDescNo, arcDescIter) ->
            arcDesc = arcDescIter  if arcDescIter.type is noNumArcType

        
        # empty default
        arcDesc = {}  unless arcDesc
        color = ((arcDesc and arcDesc.color) or (spanTypes.ARC_DEFAULT and spanTypes.ARC_DEFAULT.color) or "#000000")
        symmetric = arcDesc and arcDesc.properties and arcDesc.properties.symmetric
        dashArray = arcDesc and arcDesc.dashArray
        arrowHead = ((arcDesc and arcDesc.arrowHead) or (spanTypes.ARC_DEFAULT and spanTypes.ARC_DEFAULT.arrowHead) or "triangle,5") + "," + color
        labelArrowHead = ((arcDesc and arcDesc.labelArrow) or (spanTypes.ARC_DEFAULT and spanTypes.ARC_DEFAULT.labelArrow) or "triangle,5") + "," + color
        leftBox = rowBBox(left)
        rightBox = rowBBox(right)
        leftRow = left.chunk.row.index
        rightRow = right.chunk.row.index
        unless arrows[arrowHead]
          arrow = makeArrow(defs, arrowHead)
          arrows[arrowHead] = arrow  if arrow
        unless arrows[labelArrowHead]
          arrow = makeArrow(defs, labelArrowHead)
          arrows[labelArrowHead] = arrow  if arrow
        
        # find the next height
        height = undefined
        fromIndex2 = undefined
        toIndex2 = undefined
        if left.chunk.index is right.chunk.index
          fromIndex2 = left.towerId * 2 + left.chunk.row.heightsAdjust
          toIndex2 = right.towerId * 2 + right.chunk.row.heightsAdjust
        else
          fromIndex2 = left.towerId * 2 + 1 + left.chunk.row.heightsAdjust
          toIndex2 = right.towerId * 2 - 1 + right.chunk.row.heightsAdjust
        unless collapseArcSpace
          height = findArcHeight(fromIndex2, toIndex2, fragmentHeights)
          adjustFragmentHeights fromIndex2, toIndex2, fragmentHeights, height
          
          # Adjust the height to align with pixels when rendered
          
          # TODO: on at least Chrome, this doesn't make a difference:
          # the lines come out pixel-width even without it. Check.
          height += 0.5
        leftSlantBound = undefined
        rightSlantBound = undefined
        chunkReverse = false
        ufoCatcher = originSpan.headFragment.chunk.index is targetSpan.headFragment.chunk.index
        chunkReverse = leftBox.x + leftBox.width / 2 < rightBox.x + rightBox.width / 2  if ufoCatcher
        ufoCatcherMod = (if ufoCatcher then (if chunkReverse then -0.5 else 0.5) else 1)
        rowIndex = leftRow

        while rowIndex <= rightRow
          row = rows[rowIndex]
          if row.chunks.length
            row.hasAnnotations = true
            if collapseArcSpace
              fromIndex2R = (if rowIndex is leftRow then fromIndex2 else row.heightsStart)
              toIndex2R = (if rowIndex is rightRow then toIndex2 else row.heightsEnd)
              height = findArcHeight(fromIndex2R, toIndex2R, fragmentHeights)
            arcGroup = svg.group(row.arcs,
              "data-from": arc.origin
              "data-to": arc.target
            )
            from = undefined
            to = undefined
            if rowIndex is leftRow
              from = leftBox.x + ((if chunkReverse then 0 else leftBox.width))
            else
              from = sentNumMargin
            if rowIndex is rightRow
              to = rightBox.x + ((if chunkReverse then rightBox.width else 0))
            else
              to = canvasWidth - 2 * Configuration.visual.margin.y
            adjustHeight = true
            if collapseArcs
              arcCacheKey = arc.type + " " + rowIndex + " " + from + " " + to
              arcCacheKey = left.span.id + " " + arcCacheKey  if rowIndex is leftRow
              arcCacheKey += " " + right.span.id  if rowIndex is rightRow
              rowHeight = arcCache[arcCacheKey]
              if rowHeight isnt `undefined`
                height = rowHeight
                adjustHeight = false
              else
                arcCache[arcCacheKey] = height
            if collapseArcSpace and adjustHeight
              adjustFragmentHeights fromIndex2R, toIndex2R, fragmentHeights, height
              
              # Adjust the height to align with pixels when rendered
              
              # TODO: on at least Chrome, this doesn't make a difference:
              # the lines come out pixel-width even without it. Check.
              height += 0.5
            originType = data.spans[arc.origin].type
            arcLabels = Util.getArcLabels(spanTypes, originType, arc.type, relationTypesHash)
            labelText = Util.arcDisplayForm(spanTypes, originType, arc.type, relationTypesHash)
            
            # if (Configuration.abbrevsOn && !ufoCatcher && arcLabels) {
            if Configuration.abbrevsOn and arcLabels
              labelIdx = 1 # first abbreviation
              # strictly speaking 2*arcSlant would be needed to allow for
              # the full-width arcs to fit, but judged unabbreviated text
              # to be more important than the space for arcs.
              maxLength = (to - from) - (arcSlant)
              while sizes.arcs.widths[labelText] > maxLength and arcLabels[labelIdx]
                labelText = arcLabels[labelIdx]
                labelIdx++
            shadowGroup = undefined
            shadowGroup = svg.group(arcGroup)  if arc.shadowClass or arc.marked
            options =
              fill: color
              "data-arc-role": arc.type
              "data-arc-origin": arc.origin
              "data-arc-target": arc.target
              
              # TODO: confirm this is unused and remove.
              #'data-arc-id': arc.id,
              "data-arc-ed": arc.eventDescId

            
            # construct SVG text, showing possible trailing index
            # numbers (as in e.g. "Theme2") as subscripts
            svgText = undefined
            unless splitArcType[2]
              
              # no subscript, simple string suffices
              svgText = labelText
            else
              
              # Need to parse out possible numeric suffixes to avoid
              # duplicating number in label and its subscript
              splitLabelText = labelText.match(/^(.*?)(\d*)$/)
              noNumLabelText = splitLabelText[1]
              svgText = svg.createText()
              
              # TODO: to address issue #453, attaching options also
              # to spans, not only primary text. Make sure there
              # are no problems with this.
              svgText.span noNumLabelText, options
              subscriptSettings =
                dy: "0.3em"
                "font-size": "80%"

              
              # alternate possibility
              #                 var subscriptSettings = {
              #                   'baseline-shift': 'sub',
              #                   'font-size': '80%'
              #                 };
              $.extend subscriptSettings, options
              svgText.span splitArcType[2], subscriptSettings
            
            # guess at the correct baseline shift to get vertical centering.
            # (CSS dominant-baseline can't be used as not all SVG rendereds support it.)
            baseline_shift = sizes.arcs.height / 4
            text = svg.text(arcGroup, (from + to) / 2, -height + baseline_shift, svgText, options)
            width = sizes.arcs.widths[labelText]
            textBox =
              x: (from + to - width) / 2
              width: width
              y: -height - sizes.arcs.height / 2
              height: sizes.arcs.height

            if arc.marked
              markedRect = svg.rect(shadowGroup, textBox.x - markedArcSize, textBox.y - markedArcSize, textBox.width + 2 * markedArcSize, textBox.height + 2 * markedArcSize,
                
                # filter: 'url(#Gaussian_Blur)',
                class: "shadow_EditHighlight"
                rx: markedArcSize
                ry: markedArcSize
              )
              svg.other markedRect, "animate",
                "data-type": arc.marked
                attributeName: "fill"
                values: ((if arc.marked is "match" then highlightMatchSequence else highlightArcSequence))
                dur: highlightDuration
                repeatCount: "indefinite"
                begin: "indefinite"

            if arc.shadowClass
              svg.rect shadowGroup, textBox.x - arcLabelShadowSize, textBox.y - arcLabelShadowSize, textBox.width + 2 * arcLabelShadowSize, textBox.height + 2 * arcLabelShadowSize,
                class: "shadow_" + arc.shadowClass
                filter: "url(#Gaussian_Blur)"
                rx: arcLabelShadowRounding
                ry: arcLabelShadowRounding

            textStart = textBox.x
            textEnd = textBox.x + textBox.width
            
            # adjust by margin for arc drawing
            textStart -= Configuration.visual.arcTextMargin
            textEnd += Configuration.visual.arcTextMargin
            if from > to
              tmp = textStart
              textStart = textEnd
              textEnd = tmp
            path = undefined
            
            # don't ask
            height = (height | 0) + 0.5  if roundCoordinates
            row.maxArcHeight = height  if height > row.maxArcHeight
            myArrowHead = ((arcDesc and arcDesc.arrowHead) or (spanTypes.ARC_DEFAULT and spanTypes.ARC_DEFAULT.arrowHead))
            arrowName = ((if symmetric then myArrowHead or "none" else ((if leftToRight then "none" else myArrowHead or "triangle,5")))) + "," + color
            arrowType = arrows[arrowName]
            arrowDecl = arrowType and ("url(#" + arrowType + ")")
            arrowAtLabelAdjust = 0
            labelArrowDecl = null
            myLabelArrowHead = ((arcDesc and arcDesc.labelArrow) or (spanTypes.ARC_DEFAULT and spanTypes.ARC_DEFAULT.labelArrow))
            if myLabelArrowHead
              labelArrowName = ((if leftToRight then symmetric and myLabelArrowHead or "none" else myLabelArrowHead or "triangle,5")) + "," + color
              labelArrowSplit = labelArrowName.split(",")
              arrowAtLabelAdjust = labelArrowSplit[0] isnt "none" and parseInt(labelArrowSplit[1], 10) or 0
              labelArrowType = arrows[labelArrowName]
              labelArrowDecl = labelArrowType and ("url(#" + labelArrowType + ")")
              arrowAtLabelAdjust = -arrowAtLabelAdjust  if ufoCatcher
            arrowStart = textStart - arrowAtLabelAdjust
            path = svg.createPath().move(arrowStart, -height)
            if rowIndex is leftRow
              cornerx = from + ufoCatcherMod * arcSlant
              
              # for normal cases, should not be past textStart even if narrow
              cornerx = arrowStart - 1  if not ufoCatcher and cornerx > arrowStart - 1
              if smoothArcCurves
                controlx = (if ufoCatcher then cornerx + 2 * ufoCatcherMod * reverseArcControlx else smoothArcSteepness * from + (1 - smoothArcSteepness) * cornerx)
                endy = leftBox.y + ((if leftToRight or arc.equiv then leftBox.height / 2 else Configuration.visual.margin.y))
                
                # no curving for short lines covering short vertical
                # distances, the arrowheads can go off (#925)
                endy = -height  if Math.abs(-height - endy) < 2 and Math.abs(cornerx - from) < 5
                line = path.line(cornerx, -height).curveQ(controlx, -height, from, endy)
              else
                path.line(cornerx, -height).line from, leftBox.y + ((if leftToRight or arc.equiv then leftBox.height / 2 else Configuration.visual.margin.y))
            else
              path.line from, -height
            svg.path arcGroup, path,
              markerEnd: arrowDecl
              markerStart: labelArrowDecl
              style: "stroke: " + color
              strokeDashArray: dashArray

            if arc.marked
              svg.path shadowGroup, path,
                class: "shadow_EditHighlight_arc"
                strokeWidth: markedArcStroke
                strokeDashArray: dashArray

              svg.other markedRect, "animate",
                "data-type": arc.marked
                attributeName: "fill"
                values: ((if arc.marked is "match" then highlightMatchSequence else highlightArcSequence))
                dur: highlightDuration
                repeatCount: "indefinite"
                begin: "indefinite"

            if arc.shadowClass
              svg.path shadowGroup, path,
                class: "shadow_" + arc.shadowClass
                strokeWidth: shadowStroke
                strokeDashArray: dashArray

            unless symmetric
              myArrowHead = ((arcDesc and arcDesc.arrowHead) or (spanTypes.ARC_DEFAULT and spanTypes.ARC_DEFAULT.arrowHead))
              arrowName = ((if leftToRight then myArrowHead or "triangle,5" else "none")) + "," + color
            arrowType = arrows[arrowName]
            arrowDecl = arrowType and ("url(#" + arrowType + ")")
            arrowAtLabelAdjust = 0
            labelArrowDecl = null
            myLabelArrowHead = ((arcDesc and arcDesc.labelArrow) or (spanTypes.ARC_DEFAULT and spanTypes.ARC_DEFAULT.labelArrow))
            if myLabelArrowHead
              labelArrowName = ((if leftToRight then myLabelArrowHead or "triangle,5" else symmetric and myLabelArrowHead or "none")) + "," + color
              labelArrowSplit = labelArrowName.split(",")
              arrowAtLabelAdjust = labelArrowSplit[0] isnt "none" and parseInt(labelArrowSplit[1], 10) or 0
              labelArrowType = arrows[labelArrowName]
              labelArrowDecl = labelArrowType and ("url(#" + labelArrowType + ")")
              arrowAtLabelAdjust = -arrowAtLabelAdjust  if ufoCatcher
            arrowEnd = textEnd + arrowAtLabelAdjust
            path = svg.createPath().move(arrowEnd, -height)
            if rowIndex is rightRow
              cornerx = to - ufoCatcherMod * arcSlant
              
              # TODO: duplicates above in part, make funcs
              # for normal cases, should not be past textEnd even if narrow
              cornerx = arrowEnd + 1  if not ufoCatcher and cornerx < arrowEnd + 1
              if smoothArcCurves
                controlx = (if ufoCatcher then cornerx - 2 * ufoCatcherMod * reverseArcControlx else smoothArcSteepness * to + (1 - smoothArcSteepness) * cornerx)
                endy = rightBox.y + ((if leftToRight and not arc.equiv then Configuration.visual.margin.y else rightBox.height / 2))
                
                # no curving for short lines covering short vertical
                # distances, the arrowheads can go off (#925)
                endy = -height  if Math.abs(-height - endy) < 2 and Math.abs(cornerx - to) < 5
                path.line(cornerx, -height).curveQ controlx, -height, to, endy
              else
                path.line(cornerx, -height).line to, rightBox.y + ((if leftToRight and not arc.equiv then Configuration.visual.margin.y else rightBox.height / 2))
            else
              path.line to, -height
            svg.path arcGroup, path,
              markerEnd: arrowDecl
              markerStart: labelArrowDecl
              style: "stroke: " + color
              strokeDashArray: dashArray

            if arc.marked
              svg.path shadowGroup, path,
                class: "shadow_EditHighlight_arc"
                strokeWidth: markedArcStroke
                strokeDashArray: dashArray

            if shadowGroup
              svg.path shadowGroup, path,
                class: "shadow_" + arc.shadowClass
                strokeWidth: shadowStroke
                strokeDashArray: dashArray

          rowIndex++

      # arc rows
      # arcs
      Util.profileEnd "arcs"
      Util.profileStart "fragmentConnectors"
      $.each data.spans, (spanNo, span) ->
        numConnectors = span.fragments.length - 1
        connectorNo = 0

        while connectorNo < numConnectors
          left = span.fragments[connectorNo]
          right = span.fragments[connectorNo + 1]
          leftBox = rowBBox(left)
          rightBox = rowBBox(right)
          leftRow = left.chunk.row.index
          rightRow = right.chunk.row.index
          rowIndex = leftRow

          while rowIndex <= rightRow
            row = rows[rowIndex]
            if row.chunks.length
              row.hasAnnotations = true
              if rowIndex is leftRow
                from = leftBox.x + leftBox.width
              else
                from = sentNumMargin
              if rowIndex is rightRow
                to = rightBox.x
              else
                to = canvasWidth - 2 * Configuration.visual.margin.y
              height = leftBox.y + leftBox.height - Configuration.visual.margin.y
              
              # don't ask
              height = (height | 0) + 0.5  if roundCoordinates
              path = svg.createPath().move(from, height).line(to, height)
              svg.path row.arcs, path,
                style: "stroke: " + fragmentConnectorColor
                strokeDashArray: fragmentConnectorDashArray

            rowIndex++
          connectorNo++

      # rowIndex
      # connectorNo
      # spans
      Util.profileEnd "fragmentConnectors"
      Util.profileStart "rows"
      
      # position the rows
      y = Configuration.visual.margin.y
      sentNumGroup = svg.group(class: "sentnum")
      currentSent = undefined
      $.each rows, (rowId, row) ->
        
        # find the maximum fragment height
        $.each row.chunks, (chunkId, chunk) ->
          $.each chunk.fragments, (fragmentId, fragment) ->
            row.maxSpanHeight = fragment.height  if row.maxSpanHeight < fragment.height


        currentSent = row.sentence  if row.sentence
        
        # SLOW (#724) and replaced with calculations:
        #
        # var rowBox = row.group.getBBox();
        # // Make it work on IE
        # rowBox = { x: rowBox.x, y: rowBox.y, height: rowBox.height, width: rowBox.width };
        # // Make it work on Firefox and Opera
        # if (rowBox.height == -Infinity) {
        #   rowBox = { x: 0, y: 0, height: 0, width: 0 };
        # }
        
        # XXX TODO HACK: find out where 5 and 1.5 come from!
        # This is the fix for #724, but the numbers are guessed.
        rowBoxHeight = Math.max(row.maxArcHeight + 5, row.maxSpanHeight + 1.5) # XXX TODO HACK: why 5, 1.5?
        if row.hasAnnotations
          
          # rowBox.height = -rowBox.y + rowSpacing;
          rowBoxHeight += rowSpacing + 1.5 # XXX TODO HACK: why 1.5?
        else
          rowBoxHeight -= 5 # XXX TODO HACK: why -5?
        rowBoxHeight += rowPadding
        bgClass = undefined
        if data.markedSent[currentSent]
          
          # specifically highlighted
          bgClass = "backgroundHighlight"
        else if Configuration.textBackgrounds is "striped"
          
          # give every other sentence a different bg class
          bgClass = "background" + row.backgroundIndex
        else
          
          # plain "standard" bg
          bgClass = "background0"
        svg.rect backgroundGroup, 0, y + sizes.texts.y + sizes.texts.height, canvasWidth, rowBoxHeight + sizes.texts.height + 1,
          class: bgClass

        y += rowBoxHeight
        y += sizes.texts.height
        row.textY = y - rowPadding
        if row.sentence
          sentence_hash = new URLHash(coll, doc,
            focus: [["sent", row.sentence]]
          )
          link = svg.link(sentNumGroup, sentence_hash.getHash())
          text = svg.text(link, sentNumMargin - Configuration.visual.margin.x, y - rowPadding, "" + row.sentence,
            "data-sent": row.sentence
          )
          sentComment = data.sentComment[row.sentence]
          if sentComment
            box = text.getBBox()
            svg.remove text
            
            # TODO: using rectShadowSize, but this shadow should
            # probably have its own setting for shadow size
            shadowRect = svg.rect(sentNumGroup, box.x - rectShadowSize, box.y - rectShadowSize, box.width + 2 * rectShadowSize, box.height + 2 * rectShadowSize,
              class: "shadow_" + sentComment.type
              filter: "url(#Gaussian_Blur)"
              rx: rectShadowRounding
              ry: rectShadowRounding
              "data-sent": row.sentence
            )
            text = svg.text(sentNumGroup, sentNumMargin - Configuration.visual.margin.x, y - rowPadding, "" + row.sentence,
              "data-sent": row.sentence
            )
        rowY = y - rowPadding
        rowY = rowY | 0  if roundCoordinates
        translate row, 0, rowY
        y += Configuration.visual.margin.y

      y += Configuration.visual.margin.y
      Util.profileEnd "rows"
      Util.profileStart "chunkFinish"
      
      # chunk index sort functions for overlapping fragment drawing
      # algorithm; first for left-to-right pass, sorting primarily
      # by start offset, second for right-to-left pass by end
      # offset. Secondary sort by fragment length in both cases.
      currentChunk = undefined
      lrChunkComp = (a, b) ->
        ac = currentChunk.fragments[a]
        bc = currentChunk.fragments[b]
        startDiff = Util.cmp(ac.from, bc.from)
        (if startDiff isnt 0 then startDiff else Util.cmp(bc.to - bc.from, ac.to - ac.from))

      rlChunkComp = (a, b) ->
        ac = currentChunk.fragments[a]
        bc = currentChunk.fragments[b]
        endDiff = Util.cmp(bc.to, ac.to)
        (if endDiff isnt 0 then endDiff else Util.cmp(bc.to - bc.from, ac.to - ac.from))

      sentenceText = null
      $.each data.chunks, (chunkNo, chunk) ->
        
        # context for sort
        currentChunk = chunk
        
        # text rendering
        if chunk.sentence
          
          # svg.text(textGroup, sentenceText); // avoids jQuerySVG bug
          svg.text textGroup, 0, 0, sentenceText  if sentenceText
          sentenceText = null
        sentenceText = svg.createText()  unless sentenceText
        nextChunk = data.chunks[chunkNo + 1]
        nextSpace = (if nextChunk then nextChunk.space else "")
        sentenceText.span chunk.text + nextSpace,
          x: chunk.textX
          y: chunk.row.textY
          "data-chunk-id": chunk.index

        
        # chunk backgrounds
        if chunk.fragments.length
          orderedIdx = []
          i = chunk.fragments.length - 1

          while i >= 0
            orderedIdx.push i
            i--
          
          # Mark entity nesting height/depth (number of
          # nested/nesting entities). To account for crossing
          # brackets in a (mostly) reasonable way, determine
          # depth/height separately in a left-to-right traversal
          # and a right-to-left traversal.
          orderedIdx.sort lrChunkComp
          openFragments = []
          i = 0

          while i < orderedIdx.length
            current = chunk.fragments[orderedIdx[i]]
            current.nestingHeightLR = 0
            current.nestingDepthLR = 0
            stillOpen = []
            o = 0

            while o < openFragments.length
              if openFragments[o].to > current.from
                stillOpen.push openFragments[o]
                openFragments[o].nestingHeightLR++
              o++
            openFragments = stillOpen
            current.nestingDepthLR = openFragments.length
            openFragments.push current
            i++
          
          # re-sort for right-to-left traversal by end position
          orderedIdx.sort rlChunkComp
          openFragments = []
          i = 0

          while i < orderedIdx.length
            current = chunk.fragments[orderedIdx[i]]
            current.nestingHeightRL = 0
            current.nestingDepthRL = 0
            stillOpen = []
            o = 0

            while o < openFragments.length
              if openFragments[o].from < current.to
                stillOpen.push openFragments[o]
                openFragments[o].nestingHeightRL++
              o++
            openFragments = stillOpen
            current.nestingDepthRL = openFragments.length
            openFragments.push current
            i++
          
          # the effective depth and height are the max of those
          # for the left-to-right and right-to-left traversals.
          i = 0

          while i < orderedIdx.length
            c = chunk.fragments[orderedIdx[i]]
            c.nestingHeight = (if c.nestingHeightLR > c.nestingHeightRL then c.nestingHeightLR else c.nestingHeightRL)
            c.nestingDepth = (if c.nestingDepthLR > c.nestingDepthRL then c.nestingDepthLR else c.nestingDepthRL)
            i++
          
          # Re-order by nesting height and draw in order
          orderedIdx.sort (a, b) ->
            Util.cmp chunk.fragments[b].nestingHeight, chunk.fragments[a].nestingHeight

          i = 0

          while i < chunk.fragments.length
            fragment = chunk.fragments[orderedIdx[i]]
            spanDesc = spanTypes[fragment.span.type]
            bgColor = ((spanDesc and spanDesc.bgColor) or (spanTypes.SPAN_DEFAULT and spanTypes.SPAN_DEFAULT.bgColor) or "#ffffff")
            
            # Tweak for nesting depth/height. Recognize just three
            # levels for now: normal, nested, and nesting, where
            # nested+nesting yields normal. (Currently testing
            # minor tweak: don't shrink for depth 1 as the nesting
            # highlight will grow anyway [check nestingDepth > 1])
            shrink = 0
            if fragment.nestingDepth > 1 and fragment.nestingHeight is 0
              shrink = 1
            else shrink = -1  if fragment.nestingDepth is 0 and fragment.nestingHeight > 0
            yShrink = shrink * nestingAdjustYStepSize
            xShrink = shrink * nestingAdjustXStepSize
            
            # bit lighter
            lightBgColor = Util.adjustColorLightness(bgColor, 0.8)
            
            # tweak for Y start offset (and corresponding height
            # reduction): text rarely hits font max height, so this
            # tends to look better
            yStartTweak = 1
            
            # store to have same mouseover highlight without recalc
            fragment.highlightPos =
              x: chunk.textX + fragment.curly.from + xShrink
              y: chunk.row.textY + sizes.texts.y + yShrink + yStartTweak
              w: fragment.curly.to - fragment.curly.from - 2 * xShrink
              h: sizes.texts.height - 2 * yShrink - yStartTweak

            svg.rect highlightGroup, fragment.highlightPos.x, fragment.highlightPos.y, fragment.highlightPos.w, fragment.highlightPos.h,
              fill: lightBgColor #opacity:1,
              rx: highlightRounding.x
              ry: highlightRounding.y

            i++

      
      # svg.text(textGroup, sentenceText); // avoids jQuerySVG bug
      svg.text textGroup, 0, 0, sentenceText  if sentenceText
      
      # draw the markedText
      $.each textMarkedRows, (textRowNo, textRowDesc) -> # row, from, to
        textHighlight = svg.rect(highlightGroup, textRowDesc[1] - 2, textRowDesc[0].textY - sizes.fragments.height, textRowDesc[2] - textRowDesc[1] + 4, sizes.fragments.height + 4,
          fill: "yellow" # TODO: put into css file, as default - turn into class
        )
        
        # NOTE: changing highlightTextSequence here will give
        # different-colored highlights
        # TODO: entirely different settings for non-animations?
        markedType = textRowDesc[3]
        svg.other textHighlight, "animate",
          "data-type": markedType
          attributeName: "fill"
          values: ((if markedType is "match" then highlightMatchSequence else highlightTextSequence))
          dur: highlightDuration
          repeatCount: "indefinite"
          begin: "indefinite"


      Util.profileEnd "chunkFinish"
      Util.profileStart "finish"
      svg.path sentNumGroup, svg.createPath().move(sentNumMargin, 0).line(sentNumMargin, y)
      
      # resize the SVG
      width = maxTextWidth + sentNumMargin + 2 * Configuration.visual.margin.x + 1
      canvasWidth = width  if width > canvasWidth
      $svg.width canvasWidth
      $svg.height y
      $svgDiv.height y
      Util.profileEnd "finish"
      Util.profileEnd "render"
      Util.profileReport()
      drawing = false
      if redraw
        redraw = false
        renderDataReal()
      $svg.find("animate").each ->
        # protect against non-SMIL browsers
        @beginElement()  if @beginElement

      dispatcher.post "doneRendering", [coll, doc, args]

    renderErrors =
      unableToReadTextFile: true
      annotationFileNotFound: true
      isDirectoryError: true

    renderData = (sourceData) ->
      Util.profileEnd "invoke getDocument"
      if sourceData and sourceData.exception
        if renderErrors[sourceData.exception]
          dispatcher.post "renderError:" + sourceData.exception, [sourceData]
        else
          dispatcher.post "unknownError", [sourceData.exception]
      else
        
        # Fill in default values that don*t necessarily go over the protocol
        setSourceDataDefaults sourceData  if sourceData
        dispatcher.post "startedRendering", [coll, doc, args]
        dispatcher.post "spin"
        setTimeout (->
          try
            renderDataReal sourceData
          catch e
            
            # We are sure not to be drawing anymore, reset the state
            drawing = false
            
            # TODO: Hook printout into dispatch elsewhere?
            console.warn "Rendering terminated due to: " + e, e.stack
            dispatcher.post "renderError: Fatal", [sourceData, e]
          dispatcher.post "unspin"
        ), 0

    renderDocument = ->
      Util.profileStart "invoke getDocument"
      dispatcher.post "ajax", [
        action: "getDocument"
        collection: coll
        document: doc
      , "renderData",
        collection: coll
        document: doc
      ]

    triggerRender = ->
      if svg and ((isRenderRequested and isCollectionLoaded) or requestedData) and Visualizer.areFontsLoaded
        isRenderRequested = false
        if requestedData
          Util.profileClear()
          Util.profileStart "before render"
          renderData requestedData
        else if doc.length
          Util.profileClear()
          Util.profileStart "before render"
          renderDocument()
        else
          dispatcher.post 0, "renderError:noFileSpecified"

    requestRenderData = (sourceData) ->
      requestedData = sourceData
      triggerRender()

    collectionChanged = ->
      isCollectionLoaded = false

    gotCurrent = (_coll, _doc, _args, reloadData) ->
      coll = _coll
      doc = _doc
      args = _args
      if reloadData
        isRenderRequested = true
        triggerRender()

    
    # event handlers
    highlight = undefined
    highlightArcs = undefined
    highlightSpans = undefined
    commentId = undefined
    onMouseOver = (evt) ->
      target = $(evt.target)
      id = undefined
      if id = target.attr("data-span-id")
        commentId = id
        span = data.spans[id]
        dispatcher.post "displaySpanComment", [evt, target, id, span.type, span.attributeText, span.text, span.comment and span.comment.text, span.comment and span.comment.type, span.normalizations]
        spanDesc = spanTypes[span.type]
        bgColor = ((spanDesc and spanDesc.bgColor) or (spanTypes.SPAN_DEFAULT and spanTypes.SPAN_DEFAULT.bgColor) or "#ffffff")
        highlight = []
        $.each span.fragments, (fragmentNo, fragment) ->
          highlight.push svg.rect(highlightGroup, fragment.highlightPos.x, fragment.highlightPos.y, fragment.highlightPos.w, fragment.highlightPos.h,
            fill: bgColor
            opacity: 0.75
            rx: highlightRounding.x
            ry: highlightRounding.y
          )

        if that.arcDragOrigin
          target.parent().addClass "highlight"
        else
          highlightArcs = $svg.find("g[data-from=\"" + id + "\"], g[data-to=\"" + id + "\"]").addClass("highlight")
          spans = {}
          spans[id] = true
          spanIds = []
          $.each span.incoming, (arcNo, arc) ->
            spans[arc.origin] = true

          $.each span.outgoing, (arcNo, arc) ->
            spans[arc.target] = true

          $.each spans, (spanId, dummy) ->
            spanIds.push "rect[data-span-id=\"" + spanId + "\"]"

          highlightSpans = $svg.find(spanIds.join(", ")).parent().addClass("highlight")
        forceRedraw()
      else if not that.arcDragOrigin and (id = target.attr("data-arc-role"))
        originSpanId = target.attr("data-arc-origin")
        targetSpanId = target.attr("data-arc-target")
        role = target.attr("data-arc-role")
        symmetric = (relationTypesHash and relationTypesHash[role] and relationTypesHash[role].properties and relationTypesHash[role].properties.symmetric)
        
        # NOTE: no commentText, commentType for now
        arcEventDescId = target.attr("data-arc-ed")
        commentText = ""
        commentType = ""
        arcId = undefined
        if arcEventDescId
          eventDesc = data.eventDescs[arcEventDescId]
          comment = eventDesc.comment
          if comment
            commentText = comment.text
            commentType = comment.type
            
            # default to type if missing text
            commentText = commentType  if commentText is "" and commentType
          
          # among arcs, only ones corresponding to relations have
          # "independent" IDs
          arcId = arcEventDescId  if eventDesc.relation
        originSpanType = data.spans[originSpanId].type or ""
        targetSpanType = data.spans[targetSpanId].type or ""
        dispatcher.post "displayArcComment", [evt, target, symmetric, arcId, originSpanId, originSpanType, role, targetSpanId, targetSpanType, commentText, commentType]
        highlightArcs = $svg.find("g[data-from=\"" + originSpanId + "\"][data-to=\"" + targetSpanId + "\"]").addClass("highlight")
        highlightSpans = $($svg).find("rect[data-span-id=\"" + originSpanId + "\"], rect[data-span-id=\"" + targetSpanId + "\"]").parent().addClass("highlight")
      else if id = target.attr("data-sent")
        comment = data.sentComment[id]
        dispatcher.post "displaySentComment", [evt, target, comment.text, comment.type]  if comment

    onMouseOut = (evt) ->
      target = $(evt.target)
      target.removeClass "badTarget"
      dispatcher.post "hideComment"
      if highlight
        $.each highlight, ->
          svg.remove this

        highlight = `undefined`
      if highlightSpans
        highlightArcs.removeClass "highlight"
        highlightSpans.removeClass "highlight"
        highlightSpans = `undefined`
      forceRedraw()

    setAbbrevs = (_abbrevsOn) ->
      
      # TODO: this is a slightly weird place to tweak the configuration
      Configuration.abbrevsOn = _abbrevsOn
      dispatcher.post "configurationChanged"

    setTextBackgrounds = (_textBackgrounds) ->
      Configuration.textBackgrounds = _textBackgrounds
      dispatcher.post "configurationChanged"

    setLayoutDensity = (_density) ->
      
      #dispatcher.post('messages', [[['Setting layout density ' + _density, 'comment']]]);
      # TODO: store standard settings instead of hard-coding
      # them here (again)
      if _density < 2
        
        # dense
        Configuration.visual.margin =
          x: 1
          y: 0

        Configuration.visual.boxSpacing = 1
        Configuration.visual.curlyHeight = 1
        Configuration.visual.arcSpacing = 7
        Configuration.visual.arcStartHeight = 18
      else if _density > 2
        
        # spacious
        Configuration.visual.margin =
          x: 2
          y: 1

        Configuration.visual.boxSpacing = 3
        Configuration.visual.curlyHeight = 6
        Configuration.visual.arcSpacing = 12
        Configuration.visual.arcStartHeight = 23
      else
        
        # standard
        Configuration.visual.margin =
          x: 2
          y: 1

        Configuration.visual.boxSpacing = 1
        Configuration.visual.curlyHeight = 4
        Configuration.visual.arcSpacing = 9
        Configuration.visual.arcStartHeight = 19
      dispatcher.post "configurationChanged"

    setSvgWidth = (_width) ->
      $svgDiv.width _width
      unless Configuration.svgWidth is _width
        Configuration.svgWidth = _width
        dispatcher.post "configurationChanged"

    $svgDiv = $($svgDiv).hide()
    
    # register event listeners
    registerHandlers = (element, events) ->
      $.each events, (eventNo, eventName) ->
        element.bind eventName, (evt) ->
          dispatcher.post eventName, [evt], "all"



    registerHandlers $svgDiv, ["mouseover", "mouseout", "mousemove", "mouseup", "mousedown", "dragstart", "dblclick", "click"]
    registerHandlers $(document), ["keydown", "keypress", "touchstart", "touchend"]
    registerHandlers $(window), ["resize"]
    
    # create the svg wrapper
    $svgDiv.svg onLoad: (_svg) ->
      that.svg = svg = _svg
      $svg = $(svg._svg)
      
      # XXX HACK REMOVED - not efficient?
      #
      #              // XXX HACK to allow off-DOM SVG element creation
      #              // we need to replace the jQuery SVG*s _makeNode function
      #              // with a modified one.
      #              // Be aware of potential breakage upon jQuery SVG upgrade.
      #              svg._makeNode = function(parent, name, settings) {
      #                  // COMMENTED OUT: parent = parent || this._svg;
      #                  var node = this._svg.ownerDocument.createElementNS($.svg.svgNS, name);
      #                  for (var name in settings) {
      #                    var value = settings[name];
      #                    if (value != null && value != null &&
      #                        (typeof value != 'string' || value != '')) {
      #                      node.setAttribute($.svg._attrNames[name] || name, value);
      #                    }
      #                  }
      #                  // ADDED IN:
      #                  if (parent)
      #                    parent.appendChild(node);
      #                  return node;
      #                };
      #              
      triggerRender()

    loadSpanTypes = (types) ->
      $.each types, (typeNo, type) ->
        if type
          spanTypes[type.type] = type
          children = type.children
          loadSpanTypes children  if children and children.length


    loadAttributeTypes = (response_types) ->
      processed = {}
      $.each response_types, (aTypeNo, aType) ->
        processed[aType.type] = aType
        
        # count the values; if only one, it*s a boolean attribute
        values = []
        for i of aType.values
          values.push i  if aType.values.hasOwnProperty(i)
        aType.bool = values[0]  if values.length is 1

      processed

    loadRelationTypes = (relation_types) ->
      $.each relation_types, (relTypeNo, relType) ->
        if relType
          relationTypesHash[relType.type] = relType
          children = relType.children
          loadRelationTypes children  if children and children.length


    collectionLoaded = (response) ->
      unless response.exception
        setCollectionDefaults response
        eventAttributeTypes = loadAttributeTypes(response.event_attribute_types)
        entityAttributeTypes = loadAttributeTypes(response.entity_attribute_types)
        spanTypes = {}
        loadSpanTypes response.entity_types
        loadSpanTypes response.event_types
        loadSpanTypes response.unconfigured_types
        relationTypesHash = {}
        loadRelationTypes response.relation_types
        loadRelationTypes response.unconfigured_types
        
        # TODO XXX: isn*t the following completely redundant with
        # loadRelationTypes?
        $.each response.relation_types, (relTypeNo, relType) ->
          relationTypesHash[relType.type] = relType

        arcBundle = (response.visual_options or {}).arc_bundle or "none"
        collapseArcs = arcBundle is "all"
        collapseArcSpace = arcBundle isnt "none"
        dispatcher.post "spanAndAttributeTypesLoaded", [spanTypes, entityAttributeTypes, eventAttributeTypes, relationTypesHash]
        isCollectionLoaded = true
        triggerRender()
      else

    
    # exception on collection load; allow visualizer_ui
    # collectionLoaded to handle this
    isReloadOkay = ->
      
      # do not reload while the user is in the dialog
      not drawing

    
    # If we are yet to load our fonts, dispatch them
    unless Visualizer.areFontsLoaded
      webFontConfig =
        custom:
          
          #        'Ubuntu',
          families: ["Astloch", "PT Sans Caption", "Liberation Sans"]
          
          # For some cases, in particular for embedding, we need to
          #              allow for fonts being hosted elsewhere 
          
          #
          urls: (if webFontURLs isnt `undefined` then webFontURLs else ["static/fonts/Astloch-Bold.ttf", "static/fonts/PT_Sans-Caption-Web-Regular.ttf", "static/fonts/Liberation_Sans-Regular.ttf"])

        active: proceedWithFonts
        inactive: proceedWithFonts
        fontactive: (fontFamily, fontDescription) ->

        
        # Note: Enable for font debugging
        #console.log("font active: ", fontFamily, fontDescription);
        fontloading: (fontFamily, fontDescription) ->

      
      # Note: Enable for font debugging
      #console.log("font loading:", fontFamily, fontDescription);
      WebFont.load webFontConfig
      setTimeout (->
        unless Visualizer.areFontsLoaded
          console.error "Timeout in loading fonts"
          proceedWithFonts()
      ), fontLoadTimeout
    dispatcher.on("collectionChanged", collectionChanged).on("collectionLoaded", collectionLoaded).on("renderData", renderData).on("triggerRender", triggerRender).on("requestRenderData", requestRenderData).on("isReloadOkay", isReloadOkay).on("resetData", resetData).on("abbrevs", setAbbrevs).on("textBackgrounds", setTextBackgrounds).on("layoutDensity", setLayoutDensity).on("svgWidth", setSvgWidth).on("current", gotCurrent).on("clearSVG", clearSVG).on("mouseover", onMouseOver).on "mouseout", onMouseOut

  Visualizer.areFontsLoaded = false
  proceedWithFonts = ->
    Visualizer.areFontsLoaded = true
    
    # Note: Enable for font debugging
    #console.log("fonts done");
    Dispatcher.post "triggerRender"

  Visualizer
)(jQuery, window)
